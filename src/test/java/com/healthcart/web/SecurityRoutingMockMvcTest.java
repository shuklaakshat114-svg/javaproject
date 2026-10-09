package com.healthcart.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Spring Security Route Protection & Header Tests")
class SecurityRoutingMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Public routes are accessible without authentication")
    void testPublicRoutesPermitted() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/register"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Anonymous users attempting to access protected routes are redirected to login")
    void testAnonymousRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(get("/seller/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(get("/cart"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(get("/checkout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "buyer@test.com", roles = "BUYER")
    @DisplayName("BUYER role is forbidden (403) from accessing admin or seller routes")
    void testBuyerForbiddenFromAdminAndSeller() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/seller/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "seller@test.com", roles = "SELLER")
    @DisplayName("SELLER role is forbidden (403) from accessing admin or buyer checkout routes")
    void testSellerForbiddenFromAdminAndCheckout() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/cart"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/checkout"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@healthcart.com", roles = "ADMIN")
    @DisplayName("ADMIN role can access admin dashboard")
    void testAdminAccessAllowed() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Security response headers are emitted correctly (PRD 9.11)")
    void testSecurityHeadersPresent() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }
}
