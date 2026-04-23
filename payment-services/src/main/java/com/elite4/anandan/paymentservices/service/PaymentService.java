package com.elite4.anandan.paymentservices.service;

import com.elite4.anandan.paymentservices.document.LinkedAccountDocument;
import com.elite4.anandan.paymentservices.dto.PaymentRequest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class PaymentService {

    private final RazorpayOAuthTokenProvider tokenProvider;
    private final RestTemplate restTemplate;
    private final NotificationClient notificationClient;
    private final LinkedAccountService linkedAccountService;
    private final String keyId;
    private final String keySecret;
    private final String companyName;
    private final int platformFee;
    private final boolean routeEnabled;

    private static final String RAZORPAY_BASE_URL = "https://api.razorpay.com";

    public PaymentService(@Value("${razorpay.keyId}") String keyId,
                          @Value("${razorpay.keySecret}") String keySecret,
                          @Value("${razorpay.companyName:CoLives Connect}") String companyName,
                          @Value("${razorpay.platformFee:4900}") int platformFee,
                          @Value("${razorpay.routeEnabled:true}") boolean routeEnabled,
                          NotificationClient notificationClient,
                          LinkedAccountService linkedAccountService,
                          RazorpayOAuthTokenProvider tokenProvider,
                          RestTemplate restTemplate) {
        log.info("Initializing PaymentService with keyId={}, routeEnabled={}, platformFee={}", keyId, routeEnabled, platformFee);
        this.notificationClient = notificationClient;
        this.linkedAccountService = linkedAccountService;
        this.tokenProvider = tokenProvider;
        this.restTemplate = restTemplate;
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.companyName = companyName;
        this.platformFee = platformFee;
        this.routeEnabled = routeEnabled;
        log.info("PaymentService initialized successfully with OAuth, companyName={}", companyName);
    }

    public JSONObject createOrder(PaymentRequest request) throws Exception {
        JSONObject options = new JSONObject();
        options.put("amount", request.getAmount());
        options.put("currency", request.getCurrency() == null || request.getCurrency().isBlank() ? "INR" : request.getCurrency());
        options.put("receipt", buildReceipt(request));

        JSONObject notes = new JSONObject();
        notes.put("registrationId", safe(request.getRegistrationId()));
        notes.put("tenantName", safe(request.getTenantName()));
        notes.put("paymentFor", safe(defaultValue(request.getPaymentFor(), "monthly_rent")));
        notes.put("description", safe(request.getDescription()));
        notes.put("ownerUsername", safe(request.getOwnerUsername()));
        notes.put("coliveName", safe(request.getColiveName()));
        options.put("notes", notes);

        // Razorpay Route: add transfer to owner's linked account
        if (routeEnabled && request.getOwnerUsername() != null && request.getColiveName() != null) {
            try {
                Optional<LinkedAccountDocument> linkedAccount = linkedAccountService
                        .getPrimaryAccount(request.getOwnerUsername(), request.getColiveName());

                if (linkedAccount.isPresent() && linkedAccount.get().isRazorpaySynced()
                        && linkedAccount.get().getRazorpayAccountId() != null) {
                    LinkedAccountDocument acc = linkedAccount.get();
                    int transferAmount = request.getAmount() - platformFee;
                    if (transferAmount > 0) {
                        JSONObject transfer = new JSONObject();
                        transfer.put("account", acc.getRazorpayAccountId());
                        transfer.put("amount", transferAmount);
                        transfer.put("currency", "INR");
                        transfer.put("on_hold", 0);

                        JSONObject transferNotes = new JSONObject();
                        transferNotes.put("ownerUsername", safe(request.getOwnerUsername()));
                        transferNotes.put("coliveName", safe(request.getColiveName()));
                        transferNotes.put("tenantName", safe(request.getTenantName()));
                        transferNotes.put("platformFee", platformFee);
                        transfer.put("notes", transferNotes);

                        JSONArray transfers = new JSONArray();
                        transfers.put(transfer);
                        options.put("transfers", transfers);

                        log.info("Razorpay Route → transfer {}p to linked account {} (owner={}, colive={}), platform fee={}p",
                                transferAmount, acc.getRazorpayAccountId(), request.getOwnerUsername(),
                                request.getColiveName(), platformFee);
                    }
                } else {
                    log.warn("Razorpay Route → no synced linked account for owner={}, colive={}. Order will be created without transfer.",
                            request.getOwnerUsername(), request.getColiveName());
                }
            } catch (Exception e) {
                log.error("Razorpay Route → failed to lookup linked account for owner={}, colive={}. Proceeding without transfer.",
                        request.getOwnerUsername(), request.getColiveName(), e);
            }
        }

        log.info("Razorpay API → POST {}/v1/orders, payload={}", RAZORPAY_BASE_URL, options);

        HttpHeaders headers = tokenProvider.createAuthHeaders(keyId, keySecret);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(options.toString(), headers);

        ResponseEntity<String> response = restTemplate.exchange(
                RAZORPAY_BASE_URL + "/v1/orders", HttpMethod.POST, entity, String.class);

        JSONObject orderResponse = new JSONObject(response.getBody());
        log.info("Razorpay API → response: id={}, amount={}, currency={}, status={}, receipt={}",
                orderResponse.optString("id"), orderResponse.optInt("amount"),
                orderResponse.optString("currency"), orderResponse.optString("status"),
                orderResponse.optString("receipt"));
        return orderResponse;
    }

    public boolean verifySignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) throws Exception {
        log.info("Verifying payment signature: orderId={}, paymentId={}", razorpayOrderId, razorpayPaymentId);
        String payload = razorpayOrderId + "|" + razorpayPaymentId;
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        String expectedSignature = toHex(digest);
        boolean match = expectedSignature.equals(razorpaySignature);
        log.info("Signature verification result: {} for orderId={}", match ? "VALID" : "INVALID", razorpayOrderId);
        return match;
    }

    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank() && !"YOUR_KEY_ID".equalsIgnoreCase(keyId)
                && keySecret != null && !keySecret.isBlank() && !"YOUR_KEY_SECRET".equalsIgnoreCase(keySecret);
    }

    public String getKeyId() {
        return keyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public boolean isRouteEnabled() {
        return routeEnabled;
    }

    public int getPlatformFee() {
        return platformFee;
    }

    public boolean hasLinkedAccount(String ownerUsername, String coliveName) {
        if (ownerUsername == null || coliveName == null) return false;
        try {
            Optional<LinkedAccountDocument> acc = linkedAccountService.getPrimaryAccount(ownerUsername, coliveName);
            return acc.isPresent() && acc.get().isRazorpaySynced() && acc.get().getRazorpayAccountId() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public void notifyPaymentInitiated(PaymentRequest request, JSONObject order) {
        log.info("Notifying payment initiated: orderId={}, tenantName={}, email={}, phone={}",
                order.optString("id"), request.getTenantName(), request.getEmail(), request.getPhoneNumber());
        Map<String, Object> variables = new HashMap<>();
        variables.put("fname", defaultValue(request.getTenantName(), "Resident"));
        variables.put("amount", order.optInt("amount"));
        variables.put("orderId", order.optString("id"));
        variables.put("paymentFor", defaultValue(request.getPaymentFor(), "monthly_rent"));

        String message = "Your payment order " + order.optString("id") + " has been created for INR " + order.optInt("amount") + ".";
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            notificationClient.sendEmailWithTemplate(request.getEmail(), "Payment Initiated", "payment-success", variables);
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            notificationClient.sendSms(request.getPhoneNumber(), message);
            notificationClient.sendWhatsapp(request.getPhoneNumber(), message);
        }
        if (request.getTelegramChatId() != null && !request.getTelegramChatId().isBlank()) {
            notificationClient.sendTelegram(request.getTelegramChatId(), message);
        }
    }

    public void notifyPaymentSuccess(PaymentRequest request) {
        log.info("Notifying payment success: orderId={}, paymentId={}, tenantName={}, email={}, phone={}",
                request.getRazorpayOrderId(), request.getRazorpayPaymentId(),
                request.getTenantName(), request.getEmail(), request.getPhoneNumber());
        String tenantName = defaultValue(request.getTenantName(), "Resident");
        Map<String, Object> variables = new HashMap<>();
        variables.put("fname", tenantName);
        variables.put("amount", request.getAmount());
        variables.put("orderId", request.getRazorpayOrderId());
        variables.put("paymentId", request.getRazorpayPaymentId());
        variables.put("paymentFor", defaultValue(request.getPaymentFor(), "monthly_rent"));

        String message = "Payment received successfully. Payment ID: " + request.getRazorpayPaymentId();
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            notificationClient.sendEmailWithTemplate(request.getEmail(), "Payment Successful", "payment-success", variables);
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            notificationClient.sendSms(request.getPhoneNumber(), message);
            notificationClient.sendWhatsapp(request.getPhoneNumber(), message);
        }
        if (request.getTelegramChatId() != null && !request.getTelegramChatId().isBlank()) {
            notificationClient.sendTelegram(request.getTelegramChatId(), message);
        }
    }

    public void notifyEmail(String to, String subject, String message) {
        notificationClient.sendEmail(to, subject, message);
    }

    public void notifySms(String phoneNumber, String message) {
        notificationClient.sendSms(phoneNumber, message);
    }

    private String buildReceipt(PaymentRequest request) {
        if (request.getReceipt() != null && !request.getReceipt().isBlank()) {
            return request.getReceipt();
        }

        String registrationId = safe(request.getRegistrationId()).replaceAll("[^a-zA-Z0-9_-]", "");
        if (!registrationId.isBlank()) {
            return (registrationId + "_" + System.currentTimeMillis()).substring(0, Math.min((registrationId + "_" + System.currentTimeMillis()).length(), 40));
        }

        return "tenant_" + System.currentTimeMillis();
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String toHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}
