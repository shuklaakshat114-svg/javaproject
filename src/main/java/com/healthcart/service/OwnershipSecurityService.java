package com.healthcart.service;

import com.healthcart.domain.*;
import com.healthcart.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("ownership")
public class OwnershipSecurityService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final UserRepository userRepository;

    public OwnershipSecurityService(
            ProductRepository productRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PrescriptionRepository prescriptionRepository,
            UserRepository userRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public boolean canEditProduct(Long productId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (isAdmin(authentication)) {
            return true;
        }
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null || currentUser.getRole() != Role.ROLE_SELLER) {
            return false;
        }
        return productRepository.findById(productId)
                .map(product -> product.getSeller().getId().equals(currentUser.getId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canViewOrder(Long orderId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (isAdmin(authentication)) {
            return true;
        }
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return false;
        }
        // Buyer can view their own order
        if (currentUser.getRole() == Role.ROLE_BUYER) {
            return orderRepository.findById(orderId)
                    .map(order -> order.getBuyer().getId().equals(currentUser.getId()))
                    .orElse(false);
        }
        // Seller can view order if it contains items sold by them
        if (currentUser.getRole() == Role.ROLE_SELLER) {
            return !orderItemRepository.findByOrderIdAndSellerId(orderId, currentUser.getId()).isEmpty();
        }
        return false;
    }

    @Transactional(readOnly = true)
    public boolean canManageOrderItem(Long orderItemId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (isAdmin(authentication)) {
            return true;
        }
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null || currentUser.getRole() != Role.ROLE_SELLER) {
            return false;
        }
        return orderItemRepository.findByIdAndSellerId(orderItemId, currentUser.getId()).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean canViewPrescription(Long prescriptionId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (isAdmin(authentication)) {
            return true;
        }
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return false;
        }
        return prescriptionRepository.findById(prescriptionId)
                .map(prescription -> {
                    // Buyer can view their own prescription
                    if (prescription.getBuyer().getId().equals(currentUser.getId())) {
                        return true;
                    }
                    // Seller can view if prescription is tied to an order that contains their items
                    if (currentUser.getRole() == Role.ROLE_SELLER && prescription.getOrder() != null) {
                        return !orderItemRepository.findByOrderIdAndSellerId(prescription.getOrder().getId(), currentUser.getId()).isEmpty();
                    }
                    return false;
                })
                .orElse(false);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmailIgnoreCase(email).orElse(null);
    }
}
