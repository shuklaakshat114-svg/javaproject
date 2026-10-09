package com.healthcart.service;

import com.healthcart.domain.Product;
import com.healthcart.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ProductRepository productRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public InventoryService(
            ProductRepository productRepository,
            NotificationService notificationService,
            AuditService auditService) {
        this.productRepository = productRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Product> getLowStockProducts(Long sellerId) {
        return productRepository.findLowStockProductsBySeller(sellerId);
    }

    @Transactional(readOnly = true)
    public long countLowStockProducts(Long sellerId) {
        return productRepository.countLowStockProductsBySeller(sellerId);
    }

    @Transactional
    @PreAuthorize("@ownership.canEditProduct(#productId, authentication)")
    public Product updateStock(Long productId, int newQuantity, String actorEmail) {
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        int previousStock = product.getStock();
        product.setStock(newQuantity);
        Product saved = productRepository.save(product);

        if (saved.isLowStock() && previousStock > saved.getLowStockThreshold()) {
            notificationService.sendLowStockAlert(saved);
        }

        auditService.record(actorEmail, "INVENTORY_ADJUST", "PRODUCT", productId.toString(),
                String.format("Stock adjusted from %d to %d", previousStock, newQuantity));

        return saved;
    }

    @Transactional
    public void deductStock(Product product, int quantity) {
        if (product.getStock() < quantity) {
            throw new IllegalStateException(String.format("Insufficient stock for product '%s'. Requested: %d, Available: %d",
                    product.getName(), quantity, product.getStock()));
        }
        product.setStock(product.getStock() - quantity);
        Product saved = productRepository.save(product);

        if (saved.isLowStock()) {
            notificationService.sendLowStockAlert(saved);
        }
    }

    @Transactional
    public void restoreStock(Product product, int quantity) {
        product.setStock(product.getStock() + quantity);
        productRepository.save(product);
        log.info("Restored {} units to product #{}", quantity, product.getId());
    }
}
