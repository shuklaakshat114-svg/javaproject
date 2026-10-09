package com.healthcart.web.controller;

import com.healthcart.domain.Address;
import com.healthcart.domain.Order;
import com.healthcart.domain.User;
import com.healthcart.service.*;
import com.healthcart.web.dto.AddressForm;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

@Controller
public class BuyerController {

    private final UserService userService;
    private final CartService cartService;
    private final WishlistService wishlistService;
    private final BrowsingHistoryService browsingHistoryService;
    private final OrderService orderService;
    private final PaymentService paymentService;

    public BuyerController(
            UserService userService,
            CartService cartService,
            WishlistService wishlistService,
            BrowsingHistoryService browsingHistoryService,
            OrderService orderService,
            PaymentService paymentService) {
        this.userService = userService;
        this.cartService = cartService;
        this.wishlistService = wishlistService;
        this.browsingHistoryService = browsingHistoryService;
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    private User getAuthenticatedUser(Authentication auth) {
        return userService.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    // --- CART ---
    @GetMapping("/cart")
    public String viewCart(Model model, Authentication auth) {
        User user = getAuthenticatedUser(auth);
        CartService.CartSummary cart = cartService.getCartSummary(user.getId());

        model.addAttribute("cart", cart);
        model.addAttribute("cartCount", cart.totalItemCount());
        model.addAttribute("wishlistCount", wishlistService.getWishlistCount(user.getId()));
        return "buyer/cart";
    }

    @PostMapping("/cart/add")
    public String addToCart(
            @RequestParam Long productId,
            @RequestParam(defaultValue = "1") int quantity,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        try {
            User user = getAuthenticatedUser(auth);
            cartService.addToCart(user, productId, quantity);
            redirectAttributes.addFlashAttribute("successMessage", "Item added to your cart.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/update")
    public String updateCart(
            @RequestParam Long productId,
            @RequestParam int quantity,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        try {
            User user = getAuthenticatedUser(auth);
            cartService.updateQuantity(user, productId, quantity);
            redirectAttributes.addFlashAttribute("successMessage", "Cart updated.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String removeFromCart(
            @RequestParam Long productId,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        User user = getAuthenticatedUser(auth);
        cartService.removeFromCart(user.getId(), productId);
        redirectAttributes.addFlashAttribute("infoMessage", "Item removed from cart.");
        return "redirect:/cart";
    }

    // --- WISHLIST ---
    @GetMapping("/buyer/wishlist")
    public String viewWishlist(Model model, Authentication auth) {
        User user = getAuthenticatedUser(auth);
        model.addAttribute("wishlistItems", wishlistService.getWishlist(user.getId()));
        model.addAttribute("cartCount", cartService.getCartCount(user.getId()));
        model.addAttribute("wishlistCount", wishlistService.getWishlistCount(user.getId()));
        return "buyer/wishlist";
    }

    @PostMapping("/buyer/wishlist/toggle")
    public String toggleWishlist(
            @RequestParam Long productId,
            Authentication auth,
            @RequestHeader(value = "Referer", required = false) String referer,
            RedirectAttributes redirectAttributes) {

        User user = getAuthenticatedUser(auth);
        wishlistService.toggleWishlist(user, productId);
        redirectAttributes.addFlashAttribute("infoMessage", "Wishlist updated.");
        return "redirect:" + (referer != null ? referer : "/buyer/wishlist");
    }

    @PostMapping("/buyer/wishlist/move-to-cart")
    public String moveWishlistToCart(
            @RequestParam Long productId,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        try {
            User user = getAuthenticatedUser(auth);
            wishlistService.moveToCart(user, productId);
            redirectAttributes.addFlashAttribute("successMessage", "Item moved to cart.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/cart";
    }

    // --- BROWSING HISTORY ---
    @GetMapping("/buyer/history")
    public String viewHistory(Model model, Authentication auth) {
        User user = getAuthenticatedUser(auth);
        model.addAttribute("recentViews", browsingHistoryService.getRecentViews(user.getId(), 20));
        model.addAttribute("cartCount", cartService.getCartCount(user.getId()));
        model.addAttribute("wishlistCount", wishlistService.getWishlistCount(user.getId()));
        return "buyer/history";
    }

    @PostMapping("/buyer/history/clear")
    public String clearHistory(Authentication auth, RedirectAttributes redirectAttributes) {
        User user = getAuthenticatedUser(auth);
        browsingHistoryService.clearHistory(user.getId());
        redirectAttributes.addFlashAttribute("infoMessage", "Browsing history cleared.");
        return "redirect:/buyer/history";
    }

    // --- CHECKOUT ---
    @GetMapping("/checkout")
    public String checkout(Model model, Authentication auth) {
        User user = getAuthenticatedUser(auth);
        CartService.CartSummary cart = cartService.getCartSummary(user.getId());

        if (cart.items().isEmpty()) {
            return "redirect:/cart";
        }

        List<Address> addresses = userService.getUserAddresses(user.getId());
        model.addAttribute("cart", cart);
        model.addAttribute("addresses", addresses);
        model.addAttribute("addressForm", new AddressForm());
        model.addAttribute("idempotencyKey", UUID.randomUUID().toString());
        model.addAttribute("cartCount", cart.totalItemCount());
        model.addAttribute("wishlistCount", wishlistService.getWishlistCount(user.getId()));
        return "buyer/checkout";
    }

    @PostMapping("/checkout")
    public String processCheckout(
            @RequestParam Long addressId,
            @RequestParam String idempotencyKey,
            @RequestParam(value = "prescriptionFile", required = false) MultipartFile prescriptionFile,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        try {
            User user = getAuthenticatedUser(auth);
            Order order = orderService.placeOrder(user, addressId, idempotencyKey, prescriptionFile);
            redirectAttributes.addFlashAttribute("successMessage", "Order #" + order.getOrderNumber() + " placed successfully!");
            return "redirect:/buyer/orders/" + order.getId();
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/checkout";
        }
    }

    // --- ORDERS ---
    @GetMapping("/buyer/orders")
    public String orderHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model,
            Authentication auth) {

        User user = getAuthenticatedUser(auth);
        Page<Order> orders = orderService.getBuyerOrders(user.getId(), page, size);

        model.addAttribute("orders", orders);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", orders.getTotalPages());
        model.addAttribute("cartCount", cartService.getCartCount(user.getId()));
        model.addAttribute("wishlistCount", wishlistService.getWishlistCount(user.getId()));
        return "buyer/orders";
    }

    @GetMapping("/buyer/orders/{id}")
    @PreAuthorize("@ownership.canViewOrder(#id, authentication)")
    public String orderDetail(@PathVariable Long id, Model model, Authentication auth) {
        Order order = orderService.getOrderById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        model.addAttribute("order", order);
        model.addAttribute("publishableKey", paymentService.getPublishableKeyId());
        return "buyer/order-detail";
    }

    @PostMapping("/buyer/orders/{id}/pay")
    @PreAuthorize("@ownership.canViewOrder(#id, authentication)")
    public String simulatePayment(
            @PathVariable Long id,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        try {
            paymentService.simulateSuccessfulPayment(id, auth.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Payment simulated and verified successfully! Your order is now PAID.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/buyer/orders/" + id;
    }

    // --- ADDRESSES ---
    @GetMapping("/buyer/addresses")
    public String addresses(Model model, Authentication auth) {
        User user = getAuthenticatedUser(auth);
        model.addAttribute("addresses", userService.getUserAddresses(user.getId()));
        model.addAttribute("addressForm", new AddressForm());
        model.addAttribute("cartCount", cartService.getCartCount(user.getId()));
        model.addAttribute("wishlistCount", wishlistService.getWishlistCount(user.getId()));
        return "buyer/addresses";
    }

    @PostMapping("/buyer/addresses")
    public String addAddress(
            @Valid @ModelAttribute("addressForm") AddressForm form,
            BindingResult bindingResult,
            Authentication auth,
            RedirectAttributes redirectAttributes,
            Model model) {

        User user = getAuthenticatedUser(auth);
        if (bindingResult.hasErrors()) {
            model.addAttribute("addresses", userService.getUserAddresses(user.getId()));
            model.addAttribute("cartCount", cartService.getCartCount(user.getId()));
            model.addAttribute("wishlistCount", wishlistService.getWishlistCount(user.getId()));
            return "buyer/addresses";
        }

        userService.addAddress(user.getId(), form.getFullName(), form.getLine1(), form.getLine2(),
                form.getCity(), form.getState(), form.getPostalCode(), form.getPhone());
        redirectAttributes.addFlashAttribute("successMessage", "New address saved.");
        return "redirect:/buyer/addresses";
    }

    @PostMapping("/buyer/addresses/{id}/delete")
    public String deleteAddress(@PathVariable Long id, Authentication auth, RedirectAttributes redirectAttributes) {
        User user = getAuthenticatedUser(auth);
        userService.deleteAddress(id, user.getId());
        redirectAttributes.addFlashAttribute("infoMessage", "Address deleted.");
        return "redirect:/buyer/addresses";
    }

    @PostMapping("/buyer/addresses/{id}/default")
    public String setDefaultAddress(@PathVariable Long id, Authentication auth, RedirectAttributes redirectAttributes) {
        User user = getAuthenticatedUser(auth);
        userService.setDefaultAddress(id, user.getId());
        redirectAttributes.addFlashAttribute("successMessage", "Default address updated.");
        return "redirect:/buyer/addresses";
    }
}
