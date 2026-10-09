package com.healthcart.service;

import com.healthcart.domain.*;
import com.healthcart.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Ownership and IDOR Security Tests")
class OwnershipSecurityServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OwnershipSecurityService ownershipService;

    private User sellerA;
    private User sellerB;
    private User buyerA;
    private User buyerB;
    private User admin;

    private Authentication authSellerA;
    private Authentication authSellerB;
    private Authentication authBuyerA;
    private Authentication authBuyerB;
    private Authentication authAdmin;

    @BeforeEach
    void setUp() {
        sellerA = new User("Seller Alpha", "sellera@test.com", "hash", Role.ROLE_SELLER);
        ReflectionTestUtils.setField(sellerA, "id", 1L);

        sellerB = new User("Seller Beta", "sellerb@test.com", "hash", Role.ROLE_SELLER);
        ReflectionTestUtils.setField(sellerB, "id", 2L);

        buyerA = new User("Buyer Alice", "buyera@test.com", "hash", Role.ROLE_BUYER);
        ReflectionTestUtils.setField(buyerA, "id", 10L);

        buyerB = new User("Buyer Bob", "buyerb@test.com", "hash", Role.ROLE_BUYER);
        ReflectionTestUtils.setField(buyerB, "id", 20L);

        admin = new User("Admin Root", "admin@test.com", "hash", Role.ROLE_ADMIN);
        ReflectionTestUtils.setField(admin, "id", 99L);

        authSellerA = new UsernamePasswordAuthenticationToken(sellerA.getEmail(), "pwd",
                List.of(new SimpleGrantedAuthority("ROLE_SELLER")));
        authSellerB = new UsernamePasswordAuthenticationToken(sellerB.getEmail(), "pwd",
                List.of(new SimpleGrantedAuthority("ROLE_SELLER")));
        authBuyerA = new UsernamePasswordAuthenticationToken(buyerA.getEmail(), "pwd",
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
        authBuyerB = new UsernamePasswordAuthenticationToken(buyerB.getEmail(), "pwd",
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
        authAdmin = new UsernamePasswordAuthenticationToken(admin.getEmail(), "pwd",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("Seller A can edit own product; Seller B cannot (IDOR Guard)")
    void testProductOwnership() {
        Product prodA = new Product(sellerA, null, "Item A", "Desc", new BigDecimal("10.00"), 5, 2, false);
        ReflectionTestUtils.setField(prodA, "id", 101L);

        when(userRepository.findByEmailIgnoreCase(sellerA.getEmail())).thenReturn(Optional.of(sellerA));
        when(userRepository.findByEmailIgnoreCase(sellerB.getEmail())).thenReturn(Optional.of(sellerB));
        when(productRepository.findById(101L)).thenReturn(Optional.of(prodA));

        assertTrue(ownershipService.canEditProduct(101L, authSellerA));
        assertFalse(ownershipService.canEditProduct(101L, authSellerB));
        assertTrue(ownershipService.canEditProduct(101L, authAdmin));
    }

    @Test
    @DisplayName("Buyer A can view own order; Buyer B cannot (IDOR Guard)")
    void testOrderOwnershipBuyer() {
        Order orderA = new Order("HC-001", buyerA, "Address", new BigDecimal("50.00"), OrderStatus.PLACED, "key-1");
        ReflectionTestUtils.setField(orderA, "id", 501L);

        when(userRepository.findByEmailIgnoreCase(buyerA.getEmail())).thenReturn(Optional.of(buyerA));
        when(userRepository.findByEmailIgnoreCase(buyerB.getEmail())).thenReturn(Optional.of(buyerB));
        when(orderRepository.findById(501L)).thenReturn(Optional.of(orderA));

        assertTrue(ownershipService.canViewOrder(501L, authBuyerA));
        assertFalse(ownershipService.canViewOrder(501L, authBuyerB));
        assertTrue(ownershipService.canViewOrder(501L, authAdmin));
    }

    @Test
    @DisplayName("Seller can view order only if it contains products sold by them")
    void testOrderOwnershipSeller() {
        Order order = new Order("HC-002", buyerA, "Address", new BigDecimal("100.00"), OrderStatus.PLACED, "key-2");
        ReflectionTestUtils.setField(order, "id", 601L);

        OrderItem itemSellerA = new OrderItem(order, null, sellerA, new BigDecimal("100.00"), 1);

        when(userRepository.findByEmailIgnoreCase(sellerA.getEmail())).thenReturn(Optional.of(sellerA));
        when(userRepository.findByEmailIgnoreCase(sellerB.getEmail())).thenReturn(Optional.of(sellerB));
        when(orderItemRepository.findByOrderIdAndSellerId(601L, 1L)).thenReturn(List.of(itemSellerA));
        when(orderItemRepository.findByOrderIdAndSellerId(601L, 2L)).thenReturn(Collections.emptyList());

        assertTrue(ownershipService.canViewOrder(601L, authSellerA));
        assertFalse(ownershipService.canViewOrder(601L, authSellerB));
    }

    @Test
    @DisplayName("Buyer A can view own prescription; Buyer B cannot")
    void testPrescriptionOwnership() {
        Prescription rxA = new Prescription(null, buyerA, "rx-uuid.pdf", "rx.pdf", "application/pdf");
        ReflectionTestUtils.setField(rxA, "id", 701L);

        when(userRepository.findByEmailIgnoreCase(buyerA.getEmail())).thenReturn(Optional.of(buyerA));
        when(userRepository.findByEmailIgnoreCase(buyerB.getEmail())).thenReturn(Optional.of(buyerB));
        when(prescriptionRepository.findById(701L)).thenReturn(Optional.of(rxA));

        assertTrue(ownershipService.canViewPrescription(701L, authBuyerA));
        assertFalse(ownershipService.canViewPrescription(701L, authBuyerB));
        assertTrue(ownershipService.canViewPrescription(701L, authAdmin));
    }
}
