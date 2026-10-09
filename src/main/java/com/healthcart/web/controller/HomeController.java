package com.healthcart.web.controller;

import com.healthcart.domain.Product;
import com.healthcart.service.CartService;
import com.healthcart.service.ProductService;
import com.healthcart.service.UserService;
import com.healthcart.service.WishlistService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class HomeController {

    private final ProductService productService;
    private final CartService cartService;
    private final WishlistService wishlistService;
    private final UserService userService;

    public HomeController(
            ProductService productService,
            CartService cartService,
            WishlistService wishlistService,
            UserService userService) {
        this.productService = productService;
        this.cartService = cartService;
        this.wishlistService = wishlistService;
        this.userService = userService;
    }

    @GetMapping("/")
    public String home(Model model, Authentication authentication) {
        List<Product> featured = productService.getFeaturedProducts();
        model.addAttribute("featuredProducts", featured);
        model.addAttribute("categories", productService.getAllCategories());

        if (authentication != null && authentication.isAuthenticated()) {
            userService.findByEmail(authentication.getName()).ifPresent(user -> {
                model.addAttribute("cartCount", cartService.getCartCount(user.getId()));
                model.addAttribute("wishlistCount", wishlistService.getWishlistCount(user.getId()));
            });
        }

        return "home";
    }
}
