package com.healthcart.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Input Validation & Attack Payload Hardening Tests")
class InputValidationMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Empty registration fields trigger validation errors without 500 exception")
    void testRegisterBuyerEmptyFieldsValidation() throws Exception {
        mockMvc.perform(post("/register/buyer")
                        .with(csrf())
                        .param("name", "")
                        .param("email", "")
                        .param("password", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("buyerForm", "name", "email", "password"));
    }

    @Test
    @DisplayName("Malformed email in registration triggers validation error")
    void testRegisterBuyerMalformedEmail() throws Exception {
        mockMvc.perform(post("/register/buyer")
                        .with(csrf())
                        .param("name", "Valid Name")
                        .param("email", "not-a-valid-email")
                        .param("password", "SecurePass123!"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("buyerForm", "email"));
    }

    @Test
    @DisplayName("Short password under 8 characters is rejected")
    void testRegisterBuyerShortPassword() throws Exception {
        mockMvc.perform(post("/register/buyer")
                        .with(csrf())
                        .param("name", "Valid Name")
                        .param("email", "user@valid.com")
                        .param("password", "123"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("buyerForm", "password"));
    }

    @Test
    @WithMockUser(username = "seller@healthcart.com", roles = "SELLER")
    @DisplayName("Product creation with negative price is rejected by bean validation")
    void testCreateProductNegativePriceRejected() throws Exception {
        mockMvc.perform(post("/seller/products")
                        .with(csrf())
                        .param("name", "Test Item")
                        .param("categoryId", "1")
                        .param("description", "A medical product")
                        .param("price", "-25.00")
                        .param("stock", "10")
                        .param("lowStockThreshold", "5")
                        .param("rxRequired", "false"))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/product-form"))
                .andExpect(model().attributeHasFieldErrors("productForm", "price"));
    }

    @Test
    @WithMockUser(username = "seller@healthcart.com", roles = "SELLER")
    @DisplayName("Product creation with negative stock is rejected by bean validation")
    void testCreateProductNegativeStockRejected() throws Exception {
        mockMvc.perform(post("/seller/products")
                        .with(csrf())
                        .param("name", "Test Item")
                        .param("categoryId", "1")
                        .param("description", "A medical product")
                        .param("price", "25.00")
                        .param("stock", "-1")
                        .param("lowStockThreshold", "5")
                        .param("rxRequired", "false"))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/product-form"))
                .andExpect(model().attributeHasFieldErrors("productForm", "stock"));
    }

    @Test
    @DisplayName("State-changing requests without CSRF token are forbidden (403)")
    void testMissingCsrfTokenForbidden() throws Exception {
        mockMvc.perform(post("/register/buyer")
                        .param("name", "Test")
                        .param("email", "test@test.com")
                        .param("password", "Test@12345"))
                .andExpect(status().isForbidden());
    }
}
