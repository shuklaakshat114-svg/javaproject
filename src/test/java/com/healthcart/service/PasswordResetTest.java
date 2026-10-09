package com.healthcart.service;

import com.healthcart.domain.PasswordResetToken;
import com.healthcart.domain.Role;
import com.healthcart.domain.User;
import com.healthcart.repository.EmailVerificationTokenRepository;
import com.healthcart.repository.PasswordResetTokenRepository;
import com.healthcart.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Password Reset & Token Lifecycle Tests")
class PasswordResetTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("John Doe", "john@example.com", "oldHash", Role.ROLE_BUYER);
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    @DisplayName("Requesting password reset creates token and dispatches email")
    void testCreatePasswordResetTokenSuccess() {
        when(userRepository.findByEmailIgnoreCase("john@example.com")).thenReturn(Optional.of(user));

        userService.createPasswordResetToken("john@example.com", "http://localhost:8080");

        verify(passwordResetTokenRepository).deleteByUserId(1L);
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(notificationService).sendPasswordResetEmail(eq("john@example.com"), contains("/reset-password?token="));
    }

    @Test
    @DisplayName("Non-existent email does not reveal account absence and produces no token (Enumeration Guard)")
    void testCreatePasswordResetTokenNonExistentEmail() {
        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
                userService.createPasswordResetToken("unknown@example.com", "http://localhost:8080"));

        verify(passwordResetTokenRepository, never()).save(any());
        verify(notificationService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    @DisplayName("Valid token successfully resets password and invalidates token")
    void testResetPasswordSuccess() {
        String tokenStr = "valid-reset-token-123";
        PasswordResetToken resetToken = new PasswordResetToken(user, tokenStr, 60);

        when(passwordResetTokenRepository.findByTokenAndUsedFalse(tokenStr)).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NewSecret@123")).thenReturn("newHashedPassword");

        userService.resetPassword(tokenStr, "NewSecret@123");

        assertEquals("newHashedPassword", user.getPasswordHash());
        assertTrue(resetToken.isUsed());
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(resetToken);
    }

    @Test
    @DisplayName("Expired reset token throws exception and does not change password")
    void testResetPasswordExpiredToken() {
        String tokenStr = "expired-token-456";
        PasswordResetToken resetToken = new PasswordResetToken(user, tokenStr, 60);
        ReflectionTestUtils.setField(resetToken, "expiryDate", LocalDateTime.now().minusMinutes(5)); // expired

        when(passwordResetTokenRepository.findByTokenAndUsedFalse(tokenStr)).thenReturn(Optional.of(resetToken));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userService.resetPassword(tokenStr, "NewSecret@123"));

        assertTrue(ex.getMessage().contains("expired"));
        assertEquals("oldHash", user.getPasswordHash());
        verify(userRepository, never()).save(any());
    }
}
