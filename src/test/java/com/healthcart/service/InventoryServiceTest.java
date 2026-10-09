package com.healthcart.service;

import com.healthcart.domain.Product;
import com.healthcart.domain.Role;
import com.healthcart.domain.User;
import com.healthcart.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Inventory Management & Stock Reservation Tests")
class InventoryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private InventoryService inventoryService;

    private User seller;
    private Product product;

    @BeforeEach
    void setUp() {
        seller = new User("Pharma Seller", "seller@healthcart.com", "hash", Role.ROLE_SELLER);
        ReflectionTestUtils.setField(seller, "id", 5L);

        product = new Product(seller, null, "Paracetamol 500mg", "Desc", new BigDecimal("50.00"), 10, 5, false);
        ReflectionTestUtils.setField(product, "id", 100L);
    }

    @Test
    @DisplayName("Deducting available stock succeeds and updates stock count")
    void testDeductStockSuccess() {
        when(productRepository.save(any(Product.class))).thenReturn(product);

        inventoryService.deductStock(product, 3);

        assertEquals(7, product.getStock());
        verify(productRepository).save(product);
        verify(notificationService, never()).sendLowStockAlert(any());
    }

    @Test
    @DisplayName("Deducting stock to or below threshold triggers low stock notification")
    void testDeductStockTriggersLowStockAlert() {
        when(productRepository.save(any(Product.class))).thenReturn(product);

        // Deduct 6 units (remaining 4, threshold is 5)
        inventoryService.deductStock(product, 6);

        assertEquals(4, product.getStock());
        verify(notificationService).sendLowStockAlert(product);
    }

    @Test
    @DisplayName("Deducting more stock than available throws IllegalStateException (Overselling Guard)")
    void testDeductStockInsufficientThrowsException() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                inventoryService.deductStock(product, 15));

        assertTrue(ex.getMessage().contains("Insufficient stock"));
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Restoring stock increments quantity accurately")
    void testRestoreStockSuccess() {
        when(productRepository.save(any(Product.class))).thenReturn(product);

        inventoryService.restoreStock(product, 5);

        assertEquals(15, product.getStock());
        verify(productRepository).save(product);
    }
}
