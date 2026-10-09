package com.healthcart.repository;

import com.healthcart.domain.OrderItem;
import com.healthcart.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    List<OrderItem> findByOrderIdAndSellerId(Long orderId, Long sellerId);

    Optional<OrderItem> findByIdAndSellerId(Long id, Long sellerId);

    @Query("SELECT oi FROM OrderItem oi WHERE oi.seller.id = :sellerId " +
           "AND (:status IS NULL OR oi.fulfillmentStatus = :status) " +
           "ORDER BY oi.order.createdAt DESC")
    Page<OrderItem> findSellerOrderItems(@Param("sellerId") Long sellerId, @Param("status") OrderStatus status, Pageable pageable);

    @Query("SELECT COALESCE(SUM(oi.unitPrice * oi.quantity), 0) FROM OrderItem oi " +
           "WHERE oi.seller.id = :sellerId AND oi.order.status NOT IN ('CANCELLED', 'PAYMENT_FAILED')")
    BigDecimal calculateSellerTotalRevenue(@Param("sellerId") Long sellerId);

    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi " +
           "WHERE oi.seller.id = :sellerId AND oi.order.status NOT IN ('CANCELLED', 'PAYMENT_FAILED')")
    long countSellerTotalUnitsSold(@Param("sellerId") Long sellerId);

    @Query("SELECT COUNT(DISTINCT oi.order.id) FROM OrderItem oi " +
           "WHERE oi.seller.id = :sellerId AND oi.order.status NOT IN ('CANCELLED', 'PAYMENT_FAILED')")
    long countSellerTotalOrders(@Param("sellerId") Long sellerId);

    @Query("SELECT oi.product.name, SUM(oi.quantity), SUM(oi.unitPrice * oi.quantity) " +
           "FROM OrderItem oi WHERE oi.seller.id = :sellerId AND oi.order.status NOT IN ('CANCELLED', 'PAYMENT_FAILED') " +
           "GROUP BY oi.product.id, oi.product.name ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> findSellerTopProducts(@Param("sellerId") Long sellerId, Pageable pageable);

    @Query("SELECT oi FROM OrderItem oi WHERE oi.seller.id = :sellerId " +
           "AND oi.order.createdAt >= :sinceDate " +
           "AND oi.order.status NOT IN ('CANCELLED', 'PAYMENT_FAILED') " +
           "ORDER BY oi.order.createdAt ASC")
    List<OrderItem> findSellerItemsSince(@Param("sellerId") Long sellerId, @Param("sinceDate") LocalDateTime sinceDate);
}
