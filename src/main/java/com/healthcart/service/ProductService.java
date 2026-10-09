package com.healthcart.service;

import com.healthcart.domain.*;
import com.healthcart.repository.CategoryRepository;
import com.healthcart.repository.ProductImageRepository;
import com.healthcart.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public ProductService(
            ProductRepository productRepository,
            ProductImageRepository productImageRepository,
            CategoryRepository categoryRepository,
            FileStorageService fileStorageService,
            AuditService auditService,
            NotificationService notificationService) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.categoryRepository = categoryRepository;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public Page<Product> searchCatalog(
            String query,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean rxRequired,
            boolean inStockOnly,
            int page,
            int size,
            String sortBy) {

        int safeSize = Math.min(Math.max(size, 1), 50); // Cap size at 50 (PRD 9.6)
        Sort sort = switch (sortBy != null ? sortBy : "newest") {
            case "priceAsc" -> Sort.by(Sort.Direction.ASC, "price");
            case "priceDesc" -> Sort.by(Sort.Direction.DESC, "price");
            case "name" -> Sort.by(Sort.Direction.ASC, "name");
            default -> Sort.by(Sort.Direction.DESC, "id");
        };

        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, sort);
        return productRepository.searchCatalog(query, categoryId, minPrice, maxPrice, rxRequired, inStockOnly, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Product> getActiveProductById(Long id) {
        return productRepository.findByIdAndActiveTrue(id);
    }

    @Transactional(readOnly = true)
    public List<Product> getFeaturedProducts() {
        return productRepository.findTop8ByActiveTrueOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public Page<Product> getSellerProducts(Long sellerId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "id"));
        return productRepository.findBySellerId(sellerId, pageable);
    }

    @Transactional
    public Product createProduct(User seller, Long categoryId, String name, String description, BigDecimal price, int stock, int lowStockThreshold, boolean rxRequired, MultipartFile imageFile) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid product category"));

        Product product = new Product(seller, category, name.trim(), description, price, stock, lowStockThreshold, rxRequired);
        Product saved = productRepository.save(product);

        if (imageFile != null && !imageFile.isEmpty()) {
            FileStorageService.StoredFileResult stored = fileStorageService.storeProductImage(imageFile);
            ProductImage image = new ProductImage(saved, stored.storageKey(), stored.detectedContentType(), true);
            productImageRepository.save(image);
            saved.getImages().add(image);
        }

        auditService.record(seller.getEmail(), "PRODUCT_CREATE", "PRODUCT", saved.getId().toString(), "Created product: " + saved.getName());
        return saved;
    }

    @Transactional
    @PreAuthorize("@ownership.canEditProduct(#id, authentication)")
    public Product updateProduct(Long id, Long categoryId, String name, String description, BigDecimal price, int stock, int lowStockThreshold, boolean rxRequired, Boolean active, MultipartFile imageFile, String actorEmail) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid product category"));
            product.setCategory(category);
        }

        product.setName(name.trim());
        product.setDescription(description);
        product.setPrice(price);
        product.setStock(stock);
        product.setLowStockThreshold(lowStockThreshold);
        product.setRxRequired(rxRequired);
        if (active != null) {
            product.setActive(active);
        }

        if (imageFile != null && !imageFile.isEmpty()) {
            FileStorageService.StoredFileResult stored = fileStorageService.storeProductImage(imageFile);
            ProductImage image = new ProductImage(product, stored.storageKey(), stored.detectedContentType(), true);
            productImageRepository.save(image);
            product.getImages().add(image);
        }

        Product saved = productRepository.save(product);

        if (saved.isLowStock()) {
            notificationService.sendLowStockAlert(saved);
        }

        auditService.record(actorEmail, "PRODUCT_UPDATE", "PRODUCT", saved.getId().toString(), "Updated product: " + saved.getName());
        return saved;
    }

    @Transactional
    @PreAuthorize("@ownership.canEditProduct(#id, authentication)")
    public void deleteProduct(Long id, String actorEmail) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        productRepository.delete(product);
        auditService.record(actorEmail, "PRODUCT_DELETE", "PRODUCT", id.toString(), "Deleted product: " + product.getName());
    }

    @Transactional
    public void toggleProductActive(Long id, String actorEmail) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        product.setActive(!product.isActive());
        productRepository.save(product);
        auditService.record(actorEmail, "PRODUCT_TOGGLE_ACTIVE", "PRODUCT", id.toString(), "Status changed to: " + (product.isActive() ? "ACTIVE" : "INACTIVE"));
    }

    @Transactional(readOnly = true)
    public Page<Product> getAllProductsForAdmin(String query, Long categoryId, Pageable pageable) {
        return productRepository.searchAllForAdmin(query, categoryId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    @Transactional
    public Category createCategory(String name, String slug, String description, String icon) {
        Category category = new Category(name.trim(), slug.trim().toLowerCase(), description, icon);
        return categoryRepository.save(category);
    }
}
