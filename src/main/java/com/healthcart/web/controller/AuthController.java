package com.healthcart.web.controller;

import com.healthcart.service.UserService;
import com.healthcart.web.dto.ForgotPasswordForm;
import com.healthcart.web.dto.RegisterBuyerForm;
import com.healthcart.web.dto.RegisterSellerForm;
import com.healthcart.web.dto.ResetPasswordForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String locked,
            @RequestParam(required = false) String registered,
            @RequestParam(required = false) String resetRequested,
            @RequestParam(required = false) String resetSuccess,
            @RequestParam(required = false) String verified,
            Model model) {

        if (error != null) {
            if ("invalid_token".equals(error)) {
                model.addAttribute("errorMessage", "The password reset link is invalid or has expired. Please request a new one.");
            } else {
                model.addAttribute("errorMessage", "Invalid email address or password.");
            }
        }
        if (logout != null) {
            model.addAttribute("infoMessage", "You have been successfully logged out.");
        }
        if (locked != null) {
            model.addAttribute("errorMessage", "Your account is temporarily locked due to multiple failed login attempts. Please try again after 15 minutes.");
        }
        if (registered != null) {
            model.addAttribute("successMessage", "Account created successfully! Please sign in with your credentials.");
        }
        if (resetRequested != null) {
            model.addAttribute("infoMessage", "If an account matches that email address, password reset instructions have been sent.");
        }
        if (resetSuccess != null) {
            model.addAttribute("successMessage", "Your password has been reset successfully. Please sign in with your new password.");
        }
        if (verified != null) {
            if ("true".equalsIgnoreCase(verified)) {
                model.addAttribute("successMessage", "Your email address has been verified successfully!");
            } else {
                model.addAttribute("errorMessage", "Email verification link is invalid or has expired.");
            }
        }

        return "auth/login";
    }

    @GetMapping("/register")
    public String register(Model model) {
        if (!model.containsAttribute("buyerForm")) {
            model.addAttribute("buyerForm", new RegisterBuyerForm());
        }
        if (!model.containsAttribute("sellerForm")) {
            model.addAttribute("sellerForm", new RegisterSellerForm());
        }
        return "auth/register";
    }

    @PostMapping("/register/buyer")
    public String registerBuyer(
            @Valid @ModelAttribute("buyerForm") RegisterBuyerForm form,
            BindingResult bindingResult,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("sellerForm", new RegisterSellerForm());
            model.addAttribute("activeTab", "buyer");
            return "auth/register";
        }

        try {
            userService.registerBuyer(form.getName(), form.getEmail(), form.getPassword());
            return "redirect:/login?registered=true";
        } catch (IllegalArgumentException ex) {
            bindingResult.rejectValue("email", "duplicate.email", ex.getMessage());
            model.addAttribute("sellerForm", new RegisterSellerForm());
            model.addAttribute("activeTab", "buyer");
            return "auth/register";
        }
    }

    @PostMapping("/register/seller")
    public String registerSeller(
            @Valid @ModelAttribute("sellerForm") RegisterSellerForm form,
            BindingResult bindingResult,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("buyerForm", new RegisterBuyerForm());
            model.addAttribute("activeTab", "seller");
            return "auth/register";
        }

        try {
            userService.registerSeller(form.getName(), form.getEmail(), form.getPassword(), form.getBusinessName(), form.getLicenseNo());
            return "redirect:/login?registered=true";
        } catch (IllegalArgumentException ex) {
            bindingResult.rejectValue("email", "duplicate.email", ex.getMessage());
            model.addAttribute("buyerForm", new RegisterBuyerForm());
            model.addAttribute("activeTab", "seller");
            return "auth/register";
        }
    }

    // --- FORGOT PASSWORD (FR-A3) ---
    @GetMapping("/forgot-password")
    public String forgotPassword(Model model) {
        if (!model.containsAttribute("forgotPasswordForm")) {
            model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        }
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(
            @Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "auth/forgot-password";
        }

        String baseUrl = resolveBaseUrl(request);
        userService.createPasswordResetToken(form.getEmail(), baseUrl);

        // Generic confirmation to prevent user enumeration (PRD 9.11)
        redirectAttributes.addFlashAttribute("infoMessage",
                "If an account is associated with that email address, password reset instructions have been dispatched.");
        return "redirect:/login?resetRequested=true";
    }

    // --- RESET PASSWORD (FR-A3) ---
    @GetMapping("/reset-password")
    public String resetPassword(
            @RequestParam(required = false) String token,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (token == null || token.isBlank() || !userService.isValidPasswordResetToken(token)) {
            redirectAttributes.addFlashAttribute("errorMessage", "The reset link is invalid or has expired.");
            return "redirect:/login?error=invalid_token";
        }

        if (!model.containsAttribute("resetPasswordForm")) {
            model.addAttribute("resetPasswordForm", new ResetPasswordForm(token));
        }
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(
            @Valid @ModelAttribute("resetPasswordForm") ResetPasswordForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "auth/reset-password";
        }

        if (!form.isPasswordMatching()) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Passwords do not match");
            return "auth/reset-password";
        }

        try {
            userService.resetPassword(form.getToken(), form.getPassword());
            redirectAttributes.addFlashAttribute("successMessage", "Your password was successfully updated. Please sign in.");
            return "redirect:/login?resetSuccess=true";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/login?error=invalid_token";
        }
    }

    // --- EMAIL VERIFICATION (FR-A3) ---
    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam String token) {
        boolean verified = userService.verifyEmail(token);
        return "redirect:/login?verified=" + verified;
    }

    private String resolveBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int port = request.getServerPort();

        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            return scheme + "://" + serverName;
        }
        return scheme + "://" + serverName + ":" + port;
    }
}
