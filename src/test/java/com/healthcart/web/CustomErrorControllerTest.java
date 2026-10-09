package com.healthcart.web;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Custom Error Controller Tests")
class CustomErrorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Error dispatch with 404 attribute renders 404 error view")
    void testErrorDispatch404() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
                .andExpect(status().isOk())
                .andExpect(view().name("error/404"))
                .andExpect(model().attribute("errorCode", "404"));
    }

    @Test
    @DisplayName("Error dispatch with 403 attribute renders 403 error view")
    void testErrorDispatch403() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403))
                .andExpect(status().isOk())
                .andExpect(view().name("error/403"))
                .andExpect(model().attribute("errorCode", "403"));
    }

    @Test
    @DisplayName("Error dispatch with 429 attribute renders 429 error view")
    void testErrorDispatch429() throws Exception {
        mockMvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 429))
                .andExpect(status().isOk())
                .andExpect(view().name("error/429"))
                .andExpect(model().attribute("errorCode", "429"));
    }

    @Test
    @DisplayName("Error dispatch with default/null attribute renders 500 error view")
    void testErrorDispatchDefault500() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().isOk())
                .andExpect(view().name("error/500"))
                .andExpect(model().attribute("errorCode", "500"));
    }

    @Test
    @DisplayName("Explicit error path /error/403 renders 403 error view")
    void testExplicitError403() throws Exception {
        mockMvc.perform(get("/error/403"))
                .andExpect(status().isOk())
                .andExpect(view().name("error/403"))
                .andExpect(model().attribute("errorCode", "403"));
    }

    @Test
    @DisplayName("Explicit error path /error/404 renders 404 error view")
    void testExplicitError404() throws Exception {
        mockMvc.perform(get("/error/404"))
                .andExpect(status().isOk())
                .andExpect(view().name("error/404"))
                .andExpect(model().attribute("errorCode", "404"));
    }
}
