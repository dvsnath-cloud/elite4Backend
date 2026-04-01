package com.elite4.anandan.paymentservices.service;

import com.elite4.anandan.paymentservices.dto.PaymentRequest;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final RazorpayClient razorpayClient;
    private final NotificationClient notificationClient;
    private final String keyId;
    private final String keySecret;
    private final String companyName;

    public PaymentService(@Value("${razorpay.keyId}") String keyId,
                          @Value("${razorpay.keySecret}") String keySecret,
                          @Value("${razorpay.companyName:CoLive Connect}") String companyName,
                          NotificationClient notificationClient) throws Exception {
        this.razorpayClient = new RazorpayClient(keyId, keySecret);
        this.notificationClient = notificationClient;
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.companyName = companyName;
    }

    public Order createOrder(PaymentRequest request) throws Exception {
        JSONObject options = new JSONObject();
        options.put("amount", request.getAmount());
        options.put("currency", request.getCurrency() == null || request.getCurrency().isBlank() ? "INR" : request.getCurrency());
        options.put("receipt", buildReceipt(request));

        JSONObject notes = new JSONObject();
        notes.put("registrationId", safe(request.getRegistrationId()));
        notes.put("tenantName", safe(request.getTenantName()));
        notes.put("paymentFor", safe(defaultValue(request.getPaymentFor(), "monthly_rent")));
        notes.put("description", safe(request.getDescription()));
        options.put("notes", notes);

        return razorpayClient.orders.create(options);
    }

    public boolean verifySignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) throws Exception {
        String payload = razorpayOrderId + "|" + razorpayPaymentId;
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        String expectedSignature = toHex(digest);
        return expectedSignature.equals(razorpaySignature);
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

    public void notifyPaymentInitiated(PaymentRequest request, Order order) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("fname", defaultValue(request.getTenantName(), "Resident"));
        variables.put("amount", order.get("amount"));
        variables.put("orderId", order.get("id"));
        variables.put("paymentFor", defaultValue(request.getPaymentFor(), "monthly_rent"));

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            notificationClient.sendEmailWithTemplate(request.getEmail(), "Payment Initiated", "payment-success", variables);
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            notificationClient.sendSms(request.getPhoneNumber(), "Your payment order " + order.get("id") + " has been created for INR " + order.get("amount") + ".");
        }
    }

    public void notifyPaymentSuccess(PaymentRequest request) {
        String tenantName = defaultValue(request.getTenantName(), "Resident");
        Map<String, Object> variables = new HashMap<>();
        variables.put("fname", tenantName);
        variables.put("amount", request.getAmount());
        variables.put("orderId", request.getRazorpayOrderId());
        variables.put("paymentId", request.getRazorpayPaymentId());
        variables.put("paymentFor", defaultValue(request.getPaymentFor(), "monthly_rent"));

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            notificationClient.sendEmailWithTemplate(request.getEmail(), "Payment Successful", "payment-success", variables);
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            notificationClient.sendSms(request.getPhoneNumber(), "Payment received successfully. Payment ID: " + request.getRazorpayPaymentId());
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
