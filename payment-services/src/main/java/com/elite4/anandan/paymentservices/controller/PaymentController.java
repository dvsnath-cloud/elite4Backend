package com.elite4.anandan.paymentservices.controller;

import com.elite4.anandan.paymentservices.dto.PaymentRequest;
import com.elite4.anandan.paymentservices.dto.PaymentResponse;
import com.elite4.anandan.paymentservices.service.PaymentService;
import com.razorpay.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        log.info("GET /payments/config");
        Map<String, Object> response = new HashMap<>();
        response.put("configured", paymentService.isConfigured());
        response.put("keyId", paymentService.isConfigured() ? paymentService.getKeyId() : null);
        response.put("companyName", paymentService.getCompanyName());
        log.info("GET /payments/config → configured={}, companyName={}", response.get("configured"), response.get("companyName"));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/order")
    public ResponseEntity<?> createOrder(@RequestBody PaymentRequest request) {
        log.info("POST /payments/order → request: amount={}, currency={}, registrationId={}, tenantName={}, paymentFor={}, ownerUsername={}, coliveName={}",
                request.getAmount(), request.getCurrency(), request.getRegistrationId(),
                request.getTenantName(), request.getPaymentFor(), request.getOwnerUsername(), request.getColiveName());
        try {
            if (!paymentService.isConfigured()) {
                log.warn("POST /payments/order → Razorpay not configured");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message", "Razorpay is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET."));
            }
            if (request.getAmount() <= 0) {
                log.warn("POST /payments/order → invalid amount: {}", request.getAmount());
                return ResponseEntity.badRequest().body(Map.of("message", "Amount must be greater than zero."));
            }

            Order order = paymentService.createOrder(request);
            log.info("POST /payments/order → Razorpay order created: id={}, amount={}, status={}",
                    order.get("id"), order.get("amount"), order.get("status"));

            //paymentService.notifyPaymentInitiated(request, order);

            PaymentResponse resp = new PaymentResponse();
            resp.setOrderId(objectToString(order.get("id")));
            resp.setAmount(objectToInteger(order.get("amount")));
            resp.setCurrency(objectToString(order.get("currency")));
            resp.setStatus(objectToString(order.get("status")));
            resp.setKeyId(paymentService.getKeyId());
            resp.setCompanyName(paymentService.getCompanyName());
            resp.setReceipt(objectToString(order.get("receipt")));
            resp.setRegistrationId(request.getRegistrationId());
            resp.setTenantName(request.getTenantName());
            resp.setPaymentFor(request.getPaymentFor());
            resp.setMessage("Order created successfully.");
            log.info("POST /payments/order → 201 CREATED, orderId={}, receipt={}", resp.getOrderId(), resp.getReceipt());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (Exception e) {
            log.error("POST /payments/order → FAILED: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage() == null ? "Failed to create order." : e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody PaymentRequest request) {
        log.info("POST /payments/verify → orderId={}, paymentId={}, registrationId={}",
                request.getRazorpayOrderId(), request.getRazorpayPaymentId(), request.getRegistrationId());
        try {
            if (!paymentService.isConfigured()) {
                log.warn("POST /payments/verify → Razorpay not configured");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message", "Razorpay is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET."));
            }
            if (request.getRazorpayOrderId() == null || request.getRazorpayPaymentId() == null || request.getRazorpaySignature() == null) {
                log.warn("POST /payments/verify → incomplete payload");
                return ResponseEntity.badRequest().body(Map.of("message", "Verification payload is incomplete."));
            }

            boolean verified = paymentService.verifySignature(
                    request.getRazorpayOrderId(),
                    request.getRazorpayPaymentId(),
                    request.getRazorpaySignature()
            );

            if (!verified) {
                log.warn("POST /payments/verify → signature verification FAILED for orderId={}", request.getRazorpayOrderId());
                return ResponseEntity.badRequest().body(Map.of("message", "Payment signature verification failed."));
            }

            log.info("POST /payments/verify → signature verified for orderId={}", request.getRazorpayOrderId());
            //paymentService.notifyPaymentSuccess(request);

            PaymentResponse response = new PaymentResponse();
            response.setOrderId(request.getRazorpayOrderId());
            response.setPaymentId(request.getRazorpayPaymentId());
            response.setAmount(request.getAmount());
            response.setCurrency(request.getCurrency());
            response.setRegistrationId(request.getRegistrationId());
            response.setTenantName(request.getTenantName());
            response.setPaymentFor(request.getPaymentFor());
            response.setVerified(true);
            response.setCompanyName(paymentService.getCompanyName());
            response.setMessage("Payment verified successfully.");
            response.setVerifiedAt(Instant.now().toString());
            log.info("POST /payments/verify → 200 OK, verified=true, paymentId={}", response.getPaymentId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("POST /payments/verify → FAILED: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage() == null ? "Failed to verify payment." : e.getMessage()));
        }
    }

    private String objectToString(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        if (obj instanceof char[]) {
            return new String((char[]) obj);
        }
        return obj.toString();
    }

    private Integer objectToInteger(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        if (obj instanceof String) {
            return Integer.parseInt((String) obj);
        }
        return null;
    }
}
