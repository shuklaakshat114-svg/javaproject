package com.healthcart.service;

import com.healthcart.domain.Order;
import com.healthcart.domain.OrderStatus;
import com.healthcart.domain.Payment;
import com.healthcart.domain.WebhookEvent;
import com.healthcart.repository.OrderRepository;
import com.healthcart.repository.PaymentRepository;
import com.healthcart.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final AuditService auditService;

    @Value("${app.payment.provider:RAZORPAY}")
    private String paymentProvider;

    @Value("${app.payment.key-id:rzp_test_healthcart123}")
    private String publishableKeyId;

    @Value("${app.payment.webhook-secret:hc_secret_super_secure_key_123456}")
    private String webhookSecret;

    public PaymentService(
            PaymentRepository paymentRepository,
            WebhookEventRepository webhookEventRepository,
            OrderRepository orderRepository,
            OrderService orderService,
            AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.auditService = auditService;
    }

    public record PaymentInitResult(String providerOrderId, String publishableKey, BigDecimal amount, String currency) {}

    @Transactional
    public PaymentInitResult initiatePayment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() != OrderStatus.PLACED) {
            throw new IllegalStateException("Payment can only be initiated for orders in PLACED status");
        }

        String providerOrderId = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        Payment payment = new Payment(order, paymentProvider, providerOrderId, order.getTotalAmount(), "PENDING");
        paymentRepository.save(payment);

        return new PaymentInitResult(providerOrderId, publishableKeyId, order.getTotalAmount(), "INR");
    }

    /**
     * Verifies HMAC-SHA256 signature using constant-time compare (PRD 9.14).
     */
    public boolean verifyWebhookSignature(String payload, String signature) {
        if (payload == null || signature == null || signature.isBlank()) {
            return false;
        }

        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] signedBytes = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = HexFormat.of().formatHex(signedBytes);

            // Constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Failed to calculate webhook HMAC-SHA256 signature", e);
            return false;
        }
    }

    /**
     * Process webhook event idempotently.
     */
    @Transactional
    public boolean processWebhookEvent(String eventId, String eventType, String providerOrderId, String paymentId, BigDecimal amount, String payload) {
        // Idempotency check: if eventId already processed, return true without re-processing (PRD 9.14)
        if (webhookEventRepository.existsByEventId(eventId)) {
            log.info("Webhook event already processed (idempotency hit): {}", eventId);
            return true;
        }

        // Save event first
        WebhookEvent event = new WebhookEvent(eventId, eventType, paymentProvider, payload);
        webhookEventRepository.save(event);

        Optional<Payment> paymentOpt = paymentRepository.findByProviderOrderId(providerOrderId);
        if (paymentOpt.isEmpty()) {
            log.warn("Payment record not found for provider order id: {}", providerOrderId);
            return true;
        }

        Payment payment = paymentOpt.get();
        payment.setRawEventId(eventId);
        payment.setProviderPaymentId(paymentId);

        Order order = payment.getOrder();

        if ("payment.captured".equalsIgnoreCase(eventType) || "payment_intent.succeeded".equalsIgnoreCase(eventType)) {
            // Verify amount matches order
            if (amount != null && order.getTotalAmount().compareTo(amount) != 0) {
                log.error("PAYMENT AMOUNT MISMATCH! Order #{}: expected {}, got {}", order.getOrderNumber(), order.getTotalAmount(), amount);
                payment.setStatus("AMOUNT_MISMATCH");
                paymentRepository.save(payment);
                auditService.record("WEBHOOK", "PAYMENT_MISMATCH", "PAYMENT", payment.getId().toString(),
                        "Mismatch for order #" + order.getOrderNumber());
                return true;
            }

            payment.setStatus("CAPTURED");
            paymentRepository.save(payment);
            orderService.updateOrderStatus(order.getId(), OrderStatus.PAID, "PAYMENT_WEBHOOK");
            log.info("Order #{} marked PAID via verified webhook", order.getOrderNumber());

        } else if ("payment.failed".equalsIgnoreCase(eventType) || "payment_intent.payment_failed".equalsIgnoreCase(eventType)) {
            payment.setStatus("FAILED");
            paymentRepository.save(payment);
            orderService.updateOrderStatus(order.getId(), OrderStatus.PAYMENT_FAILED, "PAYMENT_WEBHOOK");
            log.warn("Order #{} marked PAYMENT_FAILED via webhook", order.getOrderNumber());
        }

        return true;
    }

    /**
     * Helper to simulate webhook or test payment completion directly for local development and demos.
     */
    @Transactional
    public void simulateSuccessfulPayment(Long orderId, String actorEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        String mockPaymentId = "pay_mock_" + UUID.randomUUID().toString().substring(0, 8);
        String mockOrderId = "order_mock_" + UUID.randomUUID().toString().substring(0, 8);

        Payment payment = new Payment(order, "RAZORPAY_TEST", mockOrderId, order.getTotalAmount(), "CAPTURED");
        payment.setProviderPaymentId(mockPaymentId);
        paymentRepository.save(payment);

        orderService.updateOrderStatus(order.getId(), OrderStatus.PAID, actorEmail);
    }

    public String getPublishableKeyId() {
        return publishableKeyId;
    }
}
