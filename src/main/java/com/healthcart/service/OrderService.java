package com.healthcart.service;

import com.healthcart.domain.*;
import com.healthcart.repository.AddressRepository;
import com.healthcart.repository.OrderItemRepository;
import com.healthcart.repository.OrderRepository;
import com.healthcart.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final CartService cartService;
    private final InventoryService inventoryService;
    private final OrderStateMachine orderStateMachine;
    private final PrescriptionService prescriptionService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository,
            AddressRepository addressRepository,
            CartService cartService,
            InventoryService inventoryService,
            OrderStateMachine orderStateMachine,
            PrescriptionService prescriptionService,
            NotificationService notificationService,
            AuditService auditService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
        this.addressRepository = addressRepository;
        this.cartService = cartService;
        this.inventoryService = inventoryService;
        this.orderStateMachine = orderStateMachine;
        this.prescriptionService = prescriptionService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Transactional
    public Order placeOrder(User buyer, Long addressId, String idempotencyKey, MultipartFile prescriptionFile) {
        // Idempotency check: if already processed, return existing order (PRD 9.13)
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Order> existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existingOrder.isPresent()) {
                log.info("Idempotent order placement request detected for key: {}", idempotencyKey);
                return existingOrder.get();
            }
        }

        CartService.CartSummary cart = cartService.getCartSummary(buyer.getId());
        if (cart.items().isEmpty()) {
            throw new IllegalStateException("Your cart is empty");
        }

        Address address = addressRepository.findByIdAndUserId(addressId, buyer.getId())
                .orElseThrow(() -> new IllegalArgumentException("Selected delivery address does not exist"));

        if (cart.hasRxProducts() && (prescriptionFile == null || prescriptionFile.isEmpty())) {
            throw new IllegalArgumentException("A valid prescription file is required for prescription medicines in your cart.");
        }

        // Generate human-friendly unique order number
        String orderNumber = "HC-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) +
                "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // Safe address snapshot
        String addressSnapshot = address.getFormattedAddress();

        Order order = new Order(orderNumber, buyer, addressSnapshot, cart.total(), OrderStatus.PLACED, idempotencyKey);
        Order savedOrder = orderRepository.save(order);

        // Process order items, deducting stock atomically
        for (CartItem cartItem : cart.items()) {
            Product product = cartItem.getProduct();
            inventoryService.deductStock(product, cartItem.getQuantity());

            OrderItem orderItem = new OrderItem(
                    savedOrder,
                    product,
                    product.getSeller(),
                    product.getPrice(),
                    cartItem.getQuantity()
            );
            orderItemRepository.save(orderItem);
            savedOrder.getItems().add(orderItem);
        }

        // Upload prescription if attached
        if (cart.hasRxProducts() && prescriptionFile != null && !prescriptionFile.isEmpty()) {
            Prescription prescription = prescriptionService.uploadPrescription(savedOrder, buyer, prescriptionFile);
            savedOrder.setPrescription(prescription);
        }

        // Clear cart
        cartService.clearCart(buyer.getId());

        // Notifications & Audit
        notificationService.sendOrderConfirmation(savedOrder);
        auditService.record(buyer.getEmail(), "ORDER_PLACED", "ORDER", savedOrder.getId().toString(),
                "Placed order #" + savedOrder.getOrderNumber() + " for ₹" + savedOrder.getTotalAmount());

        return savedOrder;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@ownership.canViewOrder(#id, authentication)")
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Order> getOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber);
    }

    @Transactional(readOnly = true)
    public Page<Order> getBuyerOrders(Long buyerId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 50));
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId, pageable);
    }

    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus newStatus, String actorEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        orderStateMachine.validateTransition(order.getStatus(), newStatus, order.getOrderNumber());

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);

        if (newStatus == OrderStatus.PAID) {
            order.setPaymentStatus("COMPLETED");
        } else if (newStatus == OrderStatus.PAYMENT_FAILED) {
            order.setPaymentStatus("FAILED");
            // Restore inventory
            restoreOrderInventory(order);
        } else if (newStatus == OrderStatus.CANCELLED) {
            // Restore inventory
            restoreOrderInventory(order);
        }

        // Update items fulfillment status accordingly
        for (OrderItem item : order.getItems()) {
            item.setFulfillmentStatus(newStatus);
            orderItemRepository.save(item);
        }

        orderRepository.save(order);
        notificationService.sendOrderStatusUpdate(order);
        auditService.record(actorEmail, "ORDER_STATUS_UPDATE", "ORDER", orderId.toString(),
                String.format("Status changed from %s to %s for order #%s", oldStatus, newStatus, order.getOrderNumber()));
    }

    @Transactional
    @PreAuthorize("@ownership.canManageOrderItem(#orderItemId, authentication)")
    public void updateItemFulfillmentStatus(Long orderItemId, OrderStatus newStatus, String sellerEmail) {
        OrderItem item = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found"));

        item.setFulfillmentStatus(newStatus);
        orderItemRepository.save(item);

        auditService.record(sellerEmail, "ORDER_ITEM_FULFILLMENT", "ORDER_ITEM", orderItemId.toString(),
                "Seller updated fulfillment status to " + newStatus);
    }

    private void restoreOrderInventory(Order order) {
        for (OrderItem item : order.getItems()) {
            inventoryService.restoreStock(item.getProduct(), item.getQuantity());
        }
    }

    @Transactional(readOnly = true)
    public Page<Order> searchOrdersAdmin(String query, OrderStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 50), Sort.by(Sort.Direction.DESC, "createdAt"));
        return orderRepository.searchOrdersAdmin(query, status, pageable);
    }

    @Transactional(readOnly = true)
    public Page<OrderItem> getSellerOrderItems(Long sellerId, OrderStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 50));
        return orderItemRepository.findSellerOrderItems(sellerId, status, pageable);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalPlatformRevenue() {
        return orderRepository.calculateTotalPlatformRevenue();
    }

    @Transactional(readOnly = true)
    public long getOrderCountByStatus(OrderStatus status) {
        return orderRepository.countByStatus(status);
    }
}
