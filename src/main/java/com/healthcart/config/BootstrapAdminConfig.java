package com.healthcart.config;

import com.healthcart.domain.Role;
import com.healthcart.domain.User;
import com.healthcart.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class BootstrapAdminConfig implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminConfig.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.bootstrap-email:#{null}}")
    private String bootstrapEmail;

    @Value("${app.admin.bootstrap-password:#{null}}")
    private String bootstrapPassword;

    public BootstrapAdminConfig(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (bootstrapEmail != null && !bootstrapEmail.isBlank() && bootstrapPassword != null && !bootstrapPassword.isBlank()) {
            if (!userRepository.existsByEmailIgnoreCase(bootstrapEmail)) {
                User admin = new User("Production Administrator", bootstrapEmail.trim().toLowerCase(), passwordEncoder.encode(bootstrapPassword), Role.ROLE_ADMIN);
                userRepository.save(admin);
                log.info("Production bootstrap administrator initialized for email: {}", bootstrapEmail);
            }
        } else {
            log.warn("Production bootstrap credentials APP_ADMIN_BOOTSTRAP_EMAIL / APP_ADMIN_BOOTSTRAP_PASSWORD not configured.");
        }
    }
}
