package com.healthcart.web.controller;

import com.healthcart.domain.Product;
import com.healthcart.service.BrowsingHistoryService;
import com.healthcart.service.CartService;
import com.healthcart.service.ProductService;
import com.healthcart.service.UserService;
import com.healthcart.service.WishlistService;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Controller
@RequestMapping("/products")
public class CatalogController {

    private final ProductService productService;
    private final BrowsingHistoryService browsingHistoryService;
    private final CartService cartService;
    private final WishlistService wishlistService;
    private final UserService userService;

    public CatalogController(
            ProductService productService,
            BrowsingHistoryService browsingHistoryService,
            CartService cartService,
            WishlistService wishlistService,
            UserService userService) {
        this.productService = productService;
        this.browsingHistoryService = browsingHistoryService;
        this.cartService = cartService;
        this.wishlistService = wishlistService;
        this.userService = userService;
    }

    @GetMapping
    public String catalog(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean rxRequired,
            @RequestParam(defaultValue = "false") boolean inStockOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "newest") String sortBy,
            Model model,
            Authentication authentication) {

        Page<Product> products = productService.searchCatalog(
                query, categoryId, minPrice, maxPrice, rxRequired, inStockOnly, page, size, sortBy);

        model.addAttribute("products", products);
        model.addAttribute("categories", productService.getAllCategories());
        model.addAttribute("query", query);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("rxRequired", rxRequired);
        model.addAttribute("inStockOnly", inStockOnly);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", products.getTotalPages());

        populateUserCounters(model, authentication);
        return "catalog";
    }

    @GetMapping("/{id}")
    public String productDetail(@PathVariable Long id, Model model, Authentication authentication) {
        Product product = productService.getActiveProductById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found or inactive"));

        model.addAttribute("product", product);

        if (authentication != null && authentication.isAuthenticated()) {
            userService.findByEmail(authentication.getName()).ifPresent(user -> {
                browsingHistoryService.recordView(user, product);
                model.addAttribute("inWishlist", wishlistService.isInWishlist(user.getId(), product.getId()));
                model.addAttribute("cartCount", cartService.getCartCount(user.getId()));
                model.addAttribute("wishlistCount", wishlistService.getWishlistCount(user.getId()));
            });
        }

        return "product-detail";
    }

    private void populateUserCounters(Model model, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            userService.findByEmail(authentication.getName()).ifPresent(user -> {
                model.addAttribute("cartCount", cartService.getCartCount(user.getId()));
                model.addAttribute("wishlistCount", wishlistService.getWishlistCount(user.getId()));
            });
        }
    }
}
