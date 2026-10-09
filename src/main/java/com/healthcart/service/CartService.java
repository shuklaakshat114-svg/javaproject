package com.healthcart.service;

import com.healthcart.domain.CartItem;
import com.healthcart.domain.Product;
import com.healthcart.domain.User;
import com.healthcart.repository.CartItemRepository;
import com.healthcart.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    public CartService(CartItemRepository cartItemRepository, ProductRepository productRepository) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
    }

    public record CartSummary(
            List<CartItem> items,
            BigDecimal subtotal,
            BigDecimal tax,
            BigDecimal shippingFee,
            BigDecimal total,
            boolean hasRxProducts,
            int totalItemCount
    ) {}

    @Transactional(readOnly = true)
    public CartSummary getCartSummary(Long userId) {
        List<CartItem> items = cartItemRepository.findByUserIdOrderByCreatedAtDesc(userId);
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean hasRx = false;
        int count = 0;

        for (CartItem item : items) {
            BigDecimal lineTotal = item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            subtotal = subtotal.add(lineTotal);
            if (item.getProduct().isRxRequired()) {
                hasRx = true;
            }
            count += item.getQuantity();
        }

        BigDecimal tax = subtotal.multiply(BigDecimal.valueOf(0.05)).setScale(2, java.math.RoundingMode.HALF_UP); // 5% GST
        BigDecimal shippingFee = (subtotal.compareTo(BigDecimal.valueOf(500)) >= 0 || subtotal.compareTo(BigDecimal.ZERO) == 0)
                ? BigDecimal.ZERO : BigDecimal.valueOf(50); // Free shipping above 500
        BigDecimal total = subtotal.add(tax).add(shippingFee);

        return new CartSummary(items, subtotal, tax, shippingFee, total, hasRx, count);
    }

    @Transactional
    public void addToCart(User buyer, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        Product product = productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product is not available for purchase"));

        if (product.getStock() <= 0) {
            throw new IllegalStateException("Product is currently out of stock");
        }

        Optional<CartItem> existingOpt = cartItemRepository.findByUserIdAndProductId(buyer.getId(), productId);
        if (existingOpt.isPresent()) {
            CartItem existing = existingOpt.get();
            int newQty = existing.getQuantity() + quantity;
            if (newQty > product.getStock()) {
                throw new IllegalStateException("Cannot add more units than currently in stock (" + product.getStock() + ")");
            }
            existing.setQuantity(newQty);
            cartItemRepository.save(existing);
        } else {
            if (quantity > product.getStock()) {
                throw new IllegalStateException("Requested quantity exceeds available stock (" + product.getStock() + ")");
            }
            CartItem item = new CartItem(buyer, product, quantity);
            cartItemRepository.save(item);
        }
    }

    @Transactional
    public void updateQuantity(User buyer, Long productId, int newQuantity) {
        if (newQuantity <= 0) {
            removeFromCart(buyer.getId(), productId);
            return;
        }

        Product product = productRepository.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        if (newQuantity > product.getStock()) {
            throw new IllegalStateException("Requested quantity exceeds available stock (" + product.getStock() + ")");
        }

        cartItemRepository.findByUserIdAndProductId(buyer.getId(), productId).ifPresent(item -> {
            item.setQuantity(newQuantity);
            cartItemRepository.save(item);
        });
    }

    @Transactional
    public void removeFromCart(Long userId, Long productId) {
        cartItemRepository.findByUserIdAndProductId(userId, productId)
                .ifPresent(cartItemRepository::delete);
    }

    @Transactional
    public void clearCart(Long userId) {
        cartItemRepository.deleteByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long getCartCount(Long userId) {
        return cartItemRepository.countByUserId(userId);
    }
}
