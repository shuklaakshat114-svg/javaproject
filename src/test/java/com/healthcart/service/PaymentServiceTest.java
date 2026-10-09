package com.healthcart.service;

import com.healthcart.domain.*;
import com.healthcart.repository.OrderRepository;
import com.healthcart.repository.PaymentRepository;
import com.healthcart.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Payment Service & Webhook Security Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PaymentService paymentService;

    private final String secret = "hc_secret_super_secure_key_123456";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "webhookSecret", secret);
        ReflectionTestUtils.setField(paymentService, "paymentProvider", "RAZORPAY");
        ReflectionTestUtils.setField(paymentService, "publishableKeyId", "rzp_test_key");
    }

    private String calculateExpectedHmac(String payload) throws Exception {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKey);
        byte[] signedBytes = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(signedBytes);
    }

    @Test
    @DisplayName("Valid HMAC-SHA256 signature passes verification")
    void testVerifyWebhookSignatureValid() throws Exception {
        String payload = "{\"event\":\"payment.captured\",\"id\":\"pay_12345\"}";
        String validSignature = calculateExpectedHmac(payload);

        assertTrue(paymentService.verifyWebhookSignature(payload, validSignature));
    }

    @Test
    @DisplayName("Tampered payload or invalid signature fails verification")
    void testVerifyWebhookSignatureInvalid() {
        String payload = "{\"event\":\"payment.captured\",\"id\":\"pay_12345\"}";
        String fakeSignature = "deadbeef1234567890abcdef";

        assertFalse(paymentService.verifyWebhookSignature(payload, fakeSignature));
        assertFalse(paymentService.verifyWebhookSignature(payload, ""));
        assertFalse(paymentService.verifyWebhookSignature(null, fakeSignature));
    }

    @Test
    @DisplayName("Idempotent webhook ignores previously processed event")
    void testProcessWebhookEventIdempotentDuplicate() {
        String eventId = "evt_duplicate_001";
        when(webhookEventRepository.existsByEventId(eventId)).thenReturn(true);

        boolean result = paymentService.processWebhookEvent(
                eventId, "payment.captured", "order_123", "pay_abc", new BigDecimal("500.00"), "{}");

        assertTrue(result);
        verify(webhookEventRepository, never()).save(any());
        verify(orderService, never()).updateOrderStatus(any(), any(), any());
    }

    @Test
    @DisplayName("Valid captured webhook marks payment CAPTURED and order PAID")
    void testProcessWebhookEventSuccess() {
        String eventId = "evt_new_001";
        String providerOrderId = "order_rzp_999";
        BigDecimal amount = new BigDecimal("499.00");

        User buyer = new User("Test Buyer", "buyer@test.com", "hash", Role.ROLE_BUYER);
        Order order = new Order("HC-2026-001", buyer, "Address Snapshot", amount, OrderStatus.PLACED, "key-1");
        ReflectionTestUtils.setField(order, "id", 10L);

        Payment payment = new Payment(order, "RAZORPAY", providerOrderId, amount, "PENDING");

        when(webhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(paymentRepository.findByProviderOrderId(providerOrderId)).thenReturn(Optional.of(payment));

        boolean result = paymentService.processWebhookEvent(
                eventId, "payment.captured", providerOrderId, "pay_capture_123", amount, "{}");

        assertTrue(result);
        assertEquals("CAPTURED", payment.getStatus());
        assertEquals("pay_capture_123", payment.getProviderPaymentId());
        verify(orderService).updateOrderStatus(10L, OrderStatus.PAID, "PAYMENT_WEBHOOK");
    }

    @Test
    @DisplayName("Amount mismatch flags payment as AMOUNT_MISMATCH without marking order PAID")
    void testProcessWebhookEventAmountMismatch() {
        String eventId = "evt_mismatch_001";
        String providerOrderId = "order_rzp_888";
        BigDecimal orderAmount = new BigDecimal("1000.00");
        BigDecimal tamperedAmount = new BigDecimal("10.00"); // Spoofed payment attempt

        User buyer = new User("Test Buyer", "buyer@test.com", "hash", Role.ROLE_BUYER);
        Order order = new Order("HC-2026-002", buyer, "Address Snapshot", orderAmount, OrderStatus.PLACED, "key-2");
        ReflectionTestUtils.setField(order, "id", 20L);

        Payment payment = new Payment(order, "RAZORPAY", providerOrderId, orderAmount, "PENDING");
        ReflectionTestUtils.setField(payment, "id", 100L);

        when(webhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(paymentRepository.findByProviderOrderId(providerOrderId)).thenReturn(Optional.of(payment));

        boolean result = paymentService.processWebhookEvent(
                eventId, "payment.captured", providerOrderId, "pay_bad_amount", tamperedAmount, "{}");

        assertTrue(result);
        assertEquals("AMOUNT_MISMATCH", payment.getStatus());
        verify(orderService, never()).updateOrderStatus(eq(20L), eq(OrderStatus.PAID), any());
        verify(auditService).record(eq("WEBHOOK"), eq("PAYMENT_MISMATCH"), eq("PAYMENT"), any(), any());
    }
}
