package com.healthcart.repository;

import com.healthcart.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndActiveTrue(Long id);

    Page<Product> findBySellerId(Long sellerId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.seller.id = :sellerId AND p.stock <= p.lowStockThreshold")
    List<Product> findLowStockProductsBySeller(@Param("sellerId") Long sellerId);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.seller.id = :sellerId AND p.stock <= p.lowStockThreshold")
    long countLowStockProductsBySeller(@Param("sellerId") Long sellerId);

    @Query("SELECT p FROM Product p WHERE p.active = true " +
           "AND (:query IS NULL OR :query = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
           "AND (:minPrice IS NULL OR p.price >= :minPrice) " +
           "AND (:maxPrice IS NULL OR p.price <= :maxPrice) " +
           "AND (:rxRequired IS NULL OR p.rxRequired = :rxRequired) " +
           "AND (:inStockOnly = false OR p.stock > 0)")
    Page<Product> searchCatalog(
            @Param("query") String query,
            @Param("categoryId") Long categoryId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("rxRequired") Boolean rxRequired,
            @Param("inStockOnly") boolean inStockOnly,
            Pageable pageable);

    @Query("SELECT p FROM Product p WHERE " +
           "(:query IS NULL OR :query = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId)")
    Page<Product> searchAllForAdmin(@Param("query") String query, @Param("categoryId") Long categoryId, Pageable pageable);

    List<Product> findTop8ByActiveTrueOrderByIdDesc();
}
