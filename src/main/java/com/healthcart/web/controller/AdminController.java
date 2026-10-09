package com.healthcart.web.controller;

import com.healthcart.domain.*;
import com.healthcart.service.*;
import com.healthcart.web.dto.PrescriptionReviewForm;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;
    private final ProductService productService;
    private final OrderService orderService;
    private final PrescriptionService prescriptionService;
    private final AuditService auditService;

    public AdminController(
            UserService userService,
            ProductService productService,
            OrderService orderService,
            PrescriptionService prescriptionService,
            AuditService auditService) {
        this.userService = userService;
        this.productService = productService;
        this.orderService = orderService;
        this.prescriptionService = prescriptionService;
        this.auditService = auditService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        long totalBuyers = userService.countByRole(Role.ROLE_BUYER);
        long totalSellers = userService.countByRole(Role.ROLE_SELLER);
        BigDecimal totalRevenue = orderService.getTotalPlatformRevenue();
        long pendingOrders = orderService.getOrderCountByStatus(OrderStatus.PLACED) +
                             orderService.getOrderCountByStatus(OrderStatus.PAID) +
                             orderService.getOrderCountByStatus(OrderStatus.CONFIRMED);
        long pendingPrescriptions = prescriptionService.countPending();

        model.addAttribute("totalBuyers", totalBuyers);
        model.addAttribute("totalSellers", totalSellers);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("pendingOrders", pendingOrders);
        model.addAttribute("pendingPrescriptions", pendingPrescriptions);

        return "admin/dashboard";
    }

    // --- USER MANAGEMENT (FR-AD1) ---
    @GetMapping("/users")
    public String users(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Role role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            Model model) {

        Page<User> users = userService.searchUsers(query, role, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        model.addAttribute("users", users);
        model.addAttribute("query", query);
        model.addAttribute("selectedRole", role);
        model.addAttribute("roles", Role.values());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", users.getTotalPages());
        return "admin/users";
    }

    @PostMapping("/users/{id}/toggle-status")
    public String toggleUserStatus(
            @PathVariable Long id,
            @RequestParam boolean enabled,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        try {
            userService.updateUserStatus(id, enabled, auth.getName());
            redirectAttributes.addFlashAttribute("successMessage", "User account status updated successfully.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/role")
    public String updateUserRole(
            @PathVariable Long id,
            @RequestParam Role role,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        try {
            userService.updateUserRole(id, role, auth.getName());
            redirectAttributes.addFlashAttribute("successMessage", "User role updated successfully.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(
            @PathVariable Long id,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        try {
            userService.deleteUser(id, auth.getName());
            redirectAttributes.addFlashAttribute("infoMessage", "User account deleted successfully.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    // --- PRODUCT MANAGEMENT (FR-AD2) ---
    @GetMapping("/products")
    public String products(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            Model model) {

        Page<Product> products = productService.getAllProductsForAdmin(
                query, categoryId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));

        model.addAttribute("products", products);
        model.addAttribute("categories", productService.getAllCategories());
        model.addAttribute("query", query);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", products.getTotalPages());
        return "admin/products";
    }

    @PostMapping("/products/{id}/toggle-active")
    public String toggleProductActive(
            @PathVariable Long id,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        try {
            productService.toggleProductActive(id, auth.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Product visibility updated.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/products";
    }

    @PostMapping("/products/{id}/delete")
    public String deleteProduct(
            @PathVariable Long id,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        try {
            productService.deleteProduct(id, auth.getName());
            redirectAttributes.addFlashAttribute("infoMessage", "Product removed from system.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/products";
    }

    // --- ORDER MANAGEMENT (FR-AD3) ---
    @GetMapping("/orders")
    public String orders(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            Model model) {

        Page<Order> orders = orderService.searchOrdersAdmin(query, status, page, size);
        model.addAttribute("orders", orders);
        model.addAttribute("query", query);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("orderStatuses", OrderStatus.values());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", orders.getTotalPages());
        return "admin/orders";
    }

    @GetMapping("/orders/{id}")
    public String orderDetail(@PathVariable Long id, Model model) {
        Order order = orderService.getOrderById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        model.addAttribute("order", order);
        model.addAttribute("orderStatuses", OrderStatus.values());
        return "admin/order-detail";
    }

    @PostMapping("/orders/{id}/status")
    public String updateOrderStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        try {
            orderService.updateOrderStatus(id, status, auth.getName());
            redirectAttributes.addFlashAttribute("successMessage", "Order status successfully updated to " + status.getDisplayName());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/orders/" + id;
    }

    // --- AUDIT ACTIVITY LOG (FR-AD4) ---
    @GetMapping("/activity")
    public String auditActivity(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        LocalDateTime from = (fromDate != null) ? fromDate.atStartOfDay() : null;
        LocalDateTime to = (toDate != null) ? toDate.atTime(23, 59, 59) : null;

        Page<AuditLog> logs = auditService.getLogs(actor, action, from, to, PageRequest.of(page, size));
        model.addAttribute("logs", logs);
        model.addAttribute("actor", actor);
        model.addAttribute("action", action);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", logs.getTotalPages());
        return "admin/activity";
    }

    // --- PRESCRIPTION REVIEW QUEUE ---
    @GetMapping("/prescriptions")
    public String prescriptionQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            Model model) {

        Page<Prescription> queue = prescriptionService.getPendingReviewQueue(page, size);
        model.addAttribute("queue", queue);
        model.addAttribute("reviewForm", new PrescriptionReviewForm());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", queue.getTotalPages());
        return "admin/prescriptions";
    }

    @PostMapping("/prescriptions/review")
    public String reviewPrescription(
            @Valid @ModelAttribute("reviewForm") PrescriptionReviewForm form,
            BindingResult bindingResult,
            Authentication auth,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid review decision submitted.");
            return "redirect:/admin/prescriptions";
        }

        try {
            User reviewer = userService.findByEmail(auth.getName()).orElse(null);
            prescriptionService.reviewPrescription(form.getPrescriptionId(), form.getStatus(), form.getReviewNotes(), reviewer);
            redirectAttributes.addFlashAttribute("successMessage", "Prescription review submitted: " + form.getStatus().getDisplayName());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/prescriptions";
    }
}
