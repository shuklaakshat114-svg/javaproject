package com.healthcart.config;

import com.healthcart.service.CustomUserDetailsService;
import com.healthcart.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final UserService userService;

    public SecurityConfig(CustomUserDetailsService userDetailsService, UserService userService) {
        this.userDetailsService = userDetailsService;
        this.userService = userService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Static assets & Public catalog
                        .requestMatchers(
                                "/", "/products/**", "/login", "/register/**",
                                "/forgot-password", "/reset-password", "/verify-email",
                                "/files/products/**", "/css/**", "/js/**", "/images/**",
                                "/error", "/error/**", "/actuator/health")
                        .permitAll()
                        // Webhook endpoints (signature verified at application layer)
                        .requestMatchers("/api/webhooks/**").permitAll()
                        // Role-based protected routes (PRD 9.3)
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/seller/**").hasRole("SELLER")
                        .requestMatchers("/buyer/**", "/cart/**", "/checkout//**").hasRole("BUYER")
                        // Deny by default / authenticate any remaining request
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(authenticationSuccessHandler())
                        .failureHandler(authenticationFailureHandler())
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(accessDeniedHandler()))
                .csrf(csrf -> csrf
                        // CSRF is active for all web forms, exempt only for external webhook provider
                        // callbacks
                        .ignoringRequestMatchers("/api/webhooks/**"))
                .headers(headers -> headers
                        .contentTypeOptions(ct -> {
                        })
                        .frameOptions(fo -> fo.sameOrigin())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .referrerPolicy(rp -> rp.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            String email = authentication.getName();
            userService.recordSuccessfulLogin(email);

            for (GrantedAuthority authority : authentication.getAuthorities()) {
                String role = authority.getAuthority();
                if ("ROLE_ADMIN".equals(role)) {
                    response.sendRedirect("/admin/dashboard");
                    return;
                } else if ("ROLE_SELLER".equals(role)) {
                    response.sendRedirect("/seller/dashboard");
                    return;
                } else if ("ROLE_BUYER".equals(role)) {
                    response.sendRedirect("/");
                    return;
                }
            }
            response.sendRedirect("/");
        };
    }

    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) -> {
            String email = request.getParameter("email");
            if (email != null && !email.isBlank()) {
                userService.recordFailedLogin(email.trim());
            }

            if (exception instanceof LockedException) {
                response.sendRedirect("/login?locked=true");
            } else {
                response.sendRedirect("/login?error=true");
            }
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied");
        };
    }
}
