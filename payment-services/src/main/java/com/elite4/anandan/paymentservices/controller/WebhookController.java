package com.elite4.anandan.paymentservices.controller;

import com.elite4.anandan.paymentservices.document.WebhookEventDocument;
import com.elite4.anandan.paymentservices.dto.TransferResponse;
import com.elite4.anandan.paymentservices.repository.WebhookEventRepository;
import com.elite4.anandan.paymentservices.service.LinkedAccountService;
import com.elite4.anandan.paymentservices.service.RouteService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/payments")
public class WebhookController {

    private final String webhookSecret;
    private final WebhookEventRepository webhookEventRepository;
    private final RouteService routeService;
    private final LinkedAccountService linkedAccountService;

    public WebhookController(@Value("${razorpay.webhookSecret:}") String webhookSecret,
                             WebhookEventRepository webhookEventRepository,
                             RouteService routeService,
                             LinkedAccountService linkedAccountService) {
        this.webhookSecret = webhookSecret;
        this.webhookEventRepository = webhookEventRepository;
        this.routeService = routeService;
        this.linkedAccountService = linkedAccountService;
        log.info("WebhookController initialized. webhookSecret configured={}", !webhookSecret.isBlank());
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        log.info("POST /payments/webhook → received webhook event");

        // Verify signature if webhook secret is configured
        if (!webhookSecret.isBlank()) {
            if (signature == null || signature.isBlank()) {
                log.warn("Webhook rejected: missing X-Razorpay-Signature header");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("status", "error", "message", "Missing signature"));
            }

            if (!verifyWebhookSignature(rawPayload, signature)) {
                log.warn("Webhook rejected: invalid signature");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("status", "error", "message", "Invalid signature"));
            }
            log.info("Webhook signature verified successfully");
        } else {
            log.warn("Webhook secret not configured — skipping signature verification (dev mode)");
        }

        try {
            JSONObject event = new JSONObject(rawPayload);
            String eventId = event.optString("id", "evt_" + System.currentTimeMillis());
            String eventType = event.optString("event", "unknown");

            log.info("Processing webhook event: id={}, type={}", eventId, eventType);

            // Idempotency check — skip if already processed
            if (webhookEventRepository.existsByEventId(eventId)) {
                log.info("Webhook event already processed: id={}", eventId);
                return ResponseEntity.ok(Map.of("status", "ok", "message", "Already processed"));
            }

            // Parse payment data from event payload
            JSONObject payload = event.optJSONObject("payload");
            JSONObject paymentEntity = null;
            String razorpayPaymentId = null;
            String razorpayOrderId = null;
            int amount = 0;
            String currency = "INR";
            String paymentStatus = "";

            if (payload != null) {
                JSONObject paymentWrapper = payload.optJSONObject("payment");
                if (paymentWrapper != null) {
                    paymentEntity = paymentWrapper.optJSONObject("entity");
                }
            }

            if (paymentEntity != null) {
                razorpayPaymentId = paymentEntity.optString("id", null);
                razorpayOrderId = paymentEntity.optString("order_id", null);
                amount = paymentEntity.optInt("amount", 0);
                currency = paymentEntity.optString("currency", "INR");
                paymentStatus = paymentEntity.optString("status", "");
            }

            // Save webhook event for audit
            WebhookEventDocument webhookDoc = new WebhookEventDocument();
            webhookDoc.setEventId(eventId);
            webhookDoc.setEventType(eventType);
            webhookDoc.setRazorpayPaymentId(razorpayPaymentId);
            webhookDoc.setRazorpayOrderId(razorpayOrderId);
            webhookDoc.setAmount(amount);
            webhookDoc.setCurrency(currency);
            webhookDoc.setStatus(paymentStatus);
            webhookDoc.setRawPayload(rawPayload);
            webhookDoc.setProcessed(false);
            webhookDoc.setReceivedAt(LocalDateTime.now());
            webhookEventRepository.save(webhookDoc);

            // Process based on event type
            switch (eventType) {
                case "payment.captured":
                    handlePaymentCaptured(paymentEntity, razorpayPaymentId, razorpayOrderId, amount);
                    break;
                case "payment.failed":
                    handlePaymentFailed(razorpayPaymentId, razorpayOrderId);
                    break;
                case "transfer.settled":
                    handleTransferSettled(payload);
                    break;
                case "account.activated":
                case "account.suspended":
                case "account.under_review":
                case "account.funds_on_hold":
                    handleAccountEvent(payload, eventType);
                    break;
                default:
                    log.info("Unhandled webhook event type: {}", eventType);
            }

            // Mark as processed
            webhookDoc.setProcessed(true);
            webhookDoc.setProcessedAt(LocalDateTime.now());
            webhookEventRepository.save(webhookDoc);

            return ResponseEntity.ok(Map.of("status", "ok"));

        } catch (Exception e) {
            log.error("Webhook processing failed: {}", e.getMessage(), e);
            // Return 200 to prevent Razorpay retries on processing errors
            // The event is already saved for manual review
            return ResponseEntity.ok(Map.of("status", "error", "message", "Processing failed, event logged for review"));
        }
    }

    private void handlePaymentCaptured(JSONObject paymentEntity, String razorpayPaymentId,
                                        String razorpayOrderId, int amount) {
        log.info("Processing payment.captured: paymentId={}, orderId={}, amount={}",
                razorpayPaymentId, razorpayOrderId, amount);

        if (paymentEntity == null || razorpayPaymentId == null) {
            log.warn("payment.captured event missing payment entity or paymentId");
            return;
        }

        // Extract owner/colive info from payment notes
        JSONObject notes = paymentEntity.optJSONObject("notes");
        if (notes == null) {
            log.warn("payment.captured event missing notes for paymentId={}", razorpayPaymentId);
            return;
        }

        String ownerUsername = notes.optString("ownerUsername", null);
        String coliveName = notes.optString("coliveName", null);
        String registrationId = notes.optString("registrationId", null);
        String tenantName = notes.optString("tenantName", null);

        if (ownerUsername == null || coliveName == null) {
            log.info("No ownerUsername/coliveName in payment notes for paymentId={}. " +
                    "Skipping route transfer (might be non-rent payment).", razorpayPaymentId);
            return;
        }

        // Initiate route transfer — split payment to owner minus platform fee
        TransferResponse response = routeService.initiateTransfer(
                razorpayPaymentId, razorpayOrderId, amount,
                ownerUsername, coliveName, registrationId, tenantName);

        log.info("Transfer result for paymentId={}: status={}, message={}",
                razorpayPaymentId, response.getStatus(), response.getMessage());
    }

    private void handlePaymentFailed(String razorpayPaymentId, String razorpayOrderId) {
        log.warn("Payment failed: paymentId={}, orderId={}", razorpayPaymentId, razorpayOrderId);
        // No transfer needed for failed payments. Event is logged for audit.
    }

    private void handleTransferSettled(JSONObject payload) {
        JSONObject transferWrapper = payload.optJSONObject("transfer");
        if (transferWrapper == null) return;

        JSONObject transferEntity = transferWrapper.optJSONObject("entity");
        if (transferEntity == null) return;

        String transferId = transferEntity.optString("id", null);
        if (transferId != null) {
            routeService.markTransferSettled(transferId);
            log.info("Transfer settled: transferId={}", transferId);
        }
    }

    private void handleAccountEvent(JSONObject payload, String eventType) {
        JSONObject accountWrapper = payload.optJSONObject("account");
        if (accountWrapper == null) return;

        JSONObject accountEntity = accountWrapper.optJSONObject("entity");
        if (accountEntity == null) return;

        String razorpayAccountId = accountEntity.optString("id", null);
        if (razorpayAccountId != null) {
            linkedAccountService.handleAccountWebhook(razorpayAccountId, eventType);
            log.info("Account event processed: accountId={}, event={}", razorpayAccountId, eventType);
        }
    }

    private boolean verifyWebhookSignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = toHex(digest);
            return expectedSignature.equals(signature);
        } catch (Exception e) {
            log.error("Webhook signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}
