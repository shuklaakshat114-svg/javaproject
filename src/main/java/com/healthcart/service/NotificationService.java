package com.healthcart.service;

import com.healthcart.domain.Order;
import com.healthcart.domain.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;

    public NotificationService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendLowStockAlert(Product product) {
        String recipient = product.getSeller().getEmail();
        String subject = "Low Stock Alert: " + product.getName();
        String text = String.format("Dear %s,\n\nYour product '%s' (ID: %d) has fallen to %d units in stock, which is at or below your threshold (%d).\n\nPlease restock immediately to avoid catalog disruption.\n\nBest regards,\nHealthCart Inventory Team",
                product.getSeller().getName(), product.getName(), product.getId(), product.getStock(), product.getLowStockThreshold());

        sendEmailSafely(recipient, subject, text);
    }

    public void sendOrderConfirmation(Order order) {
        String recipient = order.getBuyer().getEmail();
        String subject = "HealthCart Order Confirmation - " + order.getOrderNumber();
        String text = String.format("Dear %s,\n\nThank you for your purchase! Your order #%s has been successfully placed.\n\nTotal Amount: INR %s\nStatus: %s\n\nTrack your order in your HealthCart dashboard.\n\nBest regards,\nHealthCart Team",
                order.getBuyer().getName(), order.getOrderNumber(), order.getTotalAmount(), order.getStatus().getDisplayName());

        sendEmailSafely(recipient, subject, text);
    }

    public void sendOrderStatusUpdate(Order order) {
        String recipient = order.getBuyer().getEmail();
        String subject = "HealthCart Order Status Update - " + order.getOrderNumber();
        String text = String.format("Dear %s,\n\nYour order #%s status has been updated to: %s.\n\nYou can review complete shipping details in your account dashboard.\n\nBest regards,\nHealthCart Team",
                order.getBuyer().getName(), order.getOrderNumber(), order.getStatus().getDisplayName());

        sendEmailSafely(recipient, subject, text);
    }

    public void sendPasswordResetEmail(String recipient, String resetUrl) {
        String subject = "HealthCart - Password Reset Request";
        String text = String.format("Hello,\n\nA password reset request was received for your HealthCart account.\n\nPlease click the link below to choose a new password:\n%s\n\nThis link will expire in 60 minutes. If you did not request this, please disregard this email.\n\nBest regards,\nHealthCart Security Team",
                resetUrl);

        sendEmailSafely(recipient, subject, text);
    }

    public void sendEmailVerification(String recipient, String verificationUrl) {
        String subject = "HealthCart - Please Verify Your Email Address";
        String text = String.format("Hello,\n\nWelcome to HealthCart! Please verify your email address by clicking the link below:\n%s\n\nThis verification link will expire in 24 hours.\n\nBest regards,\nHealthCart Team",
                verificationUrl);

        sendEmailSafely(recipient, subject, text);
    }

    private void sendEmailSafely(String recipient, String subject, String text) {
        log.info("Sending notification email to: {} | Subject: {}", recipient, subject);
        if (mailSender != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(recipient);
                message.setSubject(subject);
                message.setText(text);
                mailSender.send(message);
            } catch (Exception e) {
                log.warn("Failed to dispatch live email to {} (logged instead): {}", recipient, e.getMessage());
            }
        }
    }
}
