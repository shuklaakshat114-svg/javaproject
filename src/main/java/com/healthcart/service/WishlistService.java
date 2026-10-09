package com.healthcart.service;

import com.healthcart.domain.Product;
import com.healthcart.domain.User;
import com.healthcart.domain.WishlistItem;
import com.healthcart.repository.ProductRepository;
import com.healthcart.repository.WishlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;

    public WishlistService(WishlistItemRepository wishlistItemRepository, ProductRepository productRepository, CartService cartService) {
        this.wishlistItemRepository = wishlistItemRepository;
        this.productRepository = productRepository;
        this.cartService = cartService;
    }

    @Transactional(readOnly = true)
    public List<WishlistItem> getWishlist(Long userId) {
        return wishlistItemRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void toggleWishlist(User buyer, Long productId) {
        if (wishlistItemRepository.existsByUserIdAndProductId(buyer.getId(), productId)) {
            wishlistItemRepository.deleteByUserIdAndProductId(buyer.getId(), productId);
        } else {
            Product product = productRepository.findByIdAndActiveTrue(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));
            WishlistItem item = new WishlistItem(buyer, product);
            wishlistItemRepository.save(item);
        }
    }

    @Transactional
    public void moveToCart(User buyer, Long productId) {
        cartService.addToCart(buyer, productId, 1);
        wishlistItemRepository.deleteByUserIdAndProductId(buyer.getId(), productId);
    }

    @Transactional(readOnly = true)
    public boolean isInWishlist(Long userId, Long productId) {
        return wishlistItemRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Transactional(readOnly = true)
    public long getWishlistCount(Long userId) {
        return wishlistItemRepository.countByUserId(userId);
    }
}
