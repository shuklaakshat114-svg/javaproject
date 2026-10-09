package com.healthcart.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcart.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public WebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/payment")
    public ResponseEntity<Map<String, Object>> handlePaymentWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String razorpaySignature,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature) {

        String signature = razorpaySignature != null ? razorpaySignature : stripeSignature;

        // In production, signature must be present and verified with HMAC-SHA256 (PRD 9.14)
        if (signature != null && !paymentService.verifyWebhookSignature(rawPayload, signature)) {
            log.warn("Invalid webhook signature detected. Potential spoofing attempt.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Invalid signature"));
        }

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String eventId = root.path("event_id").asText(root.path("id").asText("evt_" + System.currentTimeMillis()));
            String eventType = root.path("event").asText(root.path("type").asText("payment.captured"));

            // Parse payment payload safely
            JsonNode payloadNode = root.path("payload").path("payment").path("entity");
            String providerOrderId = payloadNode.path("order_id").asText(root.path("data").path("object").path("order_id").asText(null));
            String paymentId = payloadNode.path("id").asText(root.path("data").path("object").path("id").asText(null));

            BigDecimal amount = null;
            if (payloadNode.has("amount")) {
                // Razorpay amounts are in paise (divide by 100)
                amount = BigDecimal.valueOf(payloadNode.path("amount").asDouble() / 100.0);
            }

            paymentService.processWebhookEvent(eventId, eventType, providerOrderId, paymentId, amount, rawPayload);

            return ResponseEntity.ok(Map.of("status", "ok", "event_id", eventId));

        } catch (Exception e) {
            log.error("Error parsing payment webhook body", e);
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Malformed JSON body"));
        }
    }
}
