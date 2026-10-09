package com.healthcart.service;

import com.healthcart.domain.*;
import com.healthcart.repository.AddressRepository;
import com.healthcart.repository.SellerProfileRepository;
import com.healthcart.repository.UserRepository;
import com.healthcart.repository.EmailVerificationTokenRepository;
import com.healthcart.repository.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    private final UserRepository userRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final NotificationService notificationService;

    public UserService(
            UserRepository userRepository,
            SellerProfileRepository sellerProfileRepository,
            AddressRepository addressRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            NotificationService notificationService) {
        this.userRepository = userRepository;
        this.sellerProfileRepository = sellerProfileRepository;
        this.addressRepository = addressRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public User registerBuyer(String name, String email, String rawPassword) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("An account with this email address already exists.");
        }

        User user = new User(name.trim(), email.trim().toLowerCase(), passwordEncoder.encode(rawPassword), Role.ROLE_BUYER);
        User saved = userRepository.save(user);
        auditService.record(saved.getEmail(), "REGISTER", "USER", saved.getId().toString(), "Buyer registered");
        return saved;
    }

    @Transactional
    public User registerSeller(String name, String email, String rawPassword, String businessName, String licenseNo) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("An account with this email address already exists.");
        }

        User user = new User(name.trim(), email.trim().toLowerCase(), passwordEncoder.encode(rawPassword), Role.ROLE_SELLER);
        User savedUser = userRepository.save(user);

        SellerProfile profile = new SellerProfile(savedUser, businessName.trim(), licenseNo.trim());
        sellerProfileRepository.save(profile);

        auditService.record(savedUser.getEmail(), "REGISTER_SELLER", "USER", savedUser.getId().toString(), "Seller registered with license: " + licenseNo);
        return savedUser;
    }

    @Transactional
    public void recordFailedLogin(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
                log.warn("Account temporarily locked due to excessive failed logins: {}", email);
                auditService.record(email, "ACCOUNT_LOCKED", "USER", user.getId().toString(), "Locked after " + attempts + " failed attempts");
            }
            userRepository.save(user);
        });
    }

    @Transactional
    public void recordSuccessfulLogin(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
            auditService.record(email, "LOGIN_SUCCESS", "USER", user.getId().toString(), "User logged in successfully");
        });
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<SellerProfile> getSellerProfile(Long userId) {
        return sellerProfileRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Address> getUserAddresses(Long userId) {
        return addressRepository.findByUserIdOrderByIdDesc(userId);
    }

    @Transactional
    public Address addAddress(Long userId, String fullName, String line1, String line2, String city, String state, String postalCode, String phone) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        List<Address> existing = addressRepository.findByUserIdOrderByIdDesc(userId);
        Address address = new Address(user, fullName, line1, line2, city, state, postalCode, phone);
        if (existing.isEmpty()) {
            address.setDefault(true);
        }
        return addressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(Long addressId, Long userId) {
        addressRepository.findByIdAndUserId(addressId, userId).ifPresent(addressRepository::delete);
    }

    @Transactional
    public void setDefaultAddress(Long addressId, Long userId) {
        List<Address> addresses = addressRepository.findByUserIdOrderByIdDesc(userId);
        for (Address addr : addresses) {
            addr.setDefault(addr.getId().equals(addressId));
            addressRepository.save(addr);
        }
    }

    // Admin operations
    @Transactional(readOnly = true)
    public Page<User> searchUsers(String query, Role role, Pageable pageable) {
        return userRepository.searchUsers(query, role, pageable);
    }

    @Transactional
    public void updateUserStatus(Long userId, boolean enabled, String adminEmail) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setEnabled(enabled);
        userRepository.save(user);
        auditService.record(adminEmail, "USER_STATUS_CHANGE", "USER", userId.toString(), "Status set to: " + (enabled ? "ENABLED" : "DISABLED"));
    }

    @Transactional
    public void updateUserRole(Long userId, Role newRole, String adminEmail) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Role oldRole = user.getRole();
        user.setRole(newRole);
        userRepository.save(user);
        auditService.record(adminEmail, "USER_ROLE_CHANGE", "USER", userId.toString(), "Role changed from " + oldRole + " to " + newRole);
    }

    @Transactional
    public void deleteUser(Long userId, String adminEmail) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        // Prevent deleting the last admin or yourself
        if (user.getRole() == Role.ROLE_ADMIN && userRepository.countByRole(Role.ROLE_ADMIN) <= 1) {
            throw new IllegalStateException("Cannot delete the only remaining administrator account.");
        }
        userRepository.delete(user);
        auditService.record(adminEmail, "USER_DELETED", "USER", userId.toString(), "Deleted user: " + user.getEmail());
    }

    @Transactional(readOnly = true)
    public long countByRole(Role role) {
        return userRepository.countByRole(role);
    }

    // --- PASSWORD RESET (FR-A3) ---
    @Transactional
    public void createPasswordResetToken(String email, String appBaseUrl) {
        userRepository.findByEmailIgnoreCase(email.trim()).ifPresent(user -> {
            passwordResetTokenRepository.deleteByUserId(user.getId());
            String token = UUID.randomUUID().toString();
            PasswordResetToken resetToken = new PasswordResetToken(user, token, 60); // 60 mins expiry
            passwordResetTokenRepository.save(resetToken);

            String resetUrl = appBaseUrl + "/reset-password?token=" + token;
            notificationService.sendPasswordResetEmail(user.getEmail(), resetUrl);
            auditService.record(user.getEmail(), "PASSWORD_RESET_REQUESTED", "USER", user.getId().toString(), "Password reset link issued");
            log.info("Password reset token generated for user: {}", user.getEmail());
        });
        // Deliberately no error returned if email does not exist to avoid user enumeration (PRD 9.11)
    }

    @Transactional(readOnly = true)
    public boolean isValidPasswordResetToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return passwordResetTokenRepository.findByTokenAndUsedFalse(token.trim())
                .map(t -> !t.isExpired())
                .orElse(false);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenAndUsedFalse(token.trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired password reset token."));

        if (resetToken.isExpired()) {
            throw new IllegalArgumentException("Password reset token has expired. Please request a new one.");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        auditService.record(user.getEmail(), "PASSWORD_RESET_SUCCESS", "USER", user.getId().toString(), "Password successfully reset via token");
        log.info("Password successfully reset for user: {}", user.getEmail());
    }

    // --- EMAIL VERIFICATION (FR-A3) ---
    @Transactional
    public void createEmailVerificationToken(User user, String appBaseUrl) {
        emailVerificationTokenRepository.deleteByUserId(user.getId());
        String token = UUID.randomUUID().toString();
        EmailVerificationToken verificationToken = new EmailVerificationToken(user, token, 24); // 24 hours expiry
        emailVerificationTokenRepository.save(verificationToken);

        String verifyUrl = appBaseUrl + "/verify-email?token=" + token;
        notificationService.sendEmailVerification(user.getEmail(), verifyUrl);
        auditService.record(user.getEmail(), "EMAIL_VERIFICATION_SENT", "USER", user.getId().toString(), "Verification token sent");
    }

    @Transactional
    public boolean verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Optional<EmailVerificationToken> tokenOpt = emailVerificationTokenRepository.findByTokenAndUsedFalse(token.trim());
        if (tokenOpt.isEmpty() || tokenOpt.get().isExpired()) {
            return false;
        }

        EmailVerificationToken verificationToken = tokenOpt.get();
        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setUsed(true);
        emailVerificationTokenRepository.save(verificationToken);

        auditService.record(user.getEmail(), "EMAIL_VERIFIED", "USER", user.getId().toString(), "Email verified successfully");
        log.info("Email successfully verified for user: {}", user.getEmail());
        return true;
    }
}
