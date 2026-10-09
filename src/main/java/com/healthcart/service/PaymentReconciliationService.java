package com.healthcart.service;

import com.healthcart.domain.Order;
import com.healthcart.domain.OrderStatus;
import com.healthcart.domain.Payment;
import com.healthcart.repository.OrderRepository;
import com.healthcart.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PaymentReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final AuditService auditService;

    public PaymentReconciliationService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            OrderService orderService,
            AuditService auditService) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
        this.auditService = auditService;
    }

    /**
     * Periodic reconciliation job per PRD 9.14.
     * Identifies stale pending orders and reconciles status with payment records.
     */
    @Scheduled(fixedDelayString = "${app.payment.reconciliation-interval-ms:300000}") // Default 5 minutes
    @Transactional
    public void reconcilePendingOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(2);
        List<Order> placedOrders = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.PLACED && o.getCreatedAt().isBefore(threshold))
                .toList();

        if (placedOrders.isEmpty()) {
            return;
        }

        log.info("Running payment reconciliation for {} unconfirmed order(s) older than threshold", placedOrders.size());

        for (Order order : placedOrders) {
            List<Payment> payments = paymentRepository.findByOrderId(order.getId());
            boolean captured = payments.stream().anyMatch(p -> "CAPTURED".equalsIgnoreCase(p.getStatus()));
            if (captured) {
                log.info("Reconciling Order #{}: Payment confirmed captured, updating to PAID", order.getOrderNumber());
                orderService.updateOrderStatus(order.getId(), OrderStatus.PAID, "SYSTEM_RECONCILIATION");
                continue;
            }

            // Order abandoned for >2 hours without successful payment -> mark cancelled and release reserved stock
            try {
                log.info("Order #{} expired without payment. Cancelling and restoring inventory.", order.getOrderNumber());
                orderService.updateOrderStatus(order.getId(), OrderStatus.CANCELLED, "SYSTEM_RECONCILIATION");
                auditService.record("SYSTEM", "ORDER_TIMEOUT_CANCELLED", "ORDER", order.getId().toString(),
                        "Order #" + order.getOrderNumber() + " auto-cancelled after expiration without payment");
            } catch (Exception ex) {
                log.warn("Failed to reconcile order #{}: {}", order.getOrderNumber(), ex.getMessage());
            }
        }
    }
}
