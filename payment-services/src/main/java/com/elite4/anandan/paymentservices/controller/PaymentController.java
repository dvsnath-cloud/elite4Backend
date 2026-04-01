package com.elite4.anandan.paymentservices.controller;

import com.elite4.anandan.paymentservices.dto.PaymentRequest;
import com.elite4.anandan.paymentservices.dto.PaymentResponse;
import com.elite4.anandan.paymentservices.service.PaymentService;
import com.razorpay.Order;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> response = new HashMap<>();
        response.put("configured", paymentService.isConfigured());
        response.put("keyId", paymentService.isConfigured() ? paymentService.getKeyId() : null);
        response.put("companyName", paymentService.getCompanyName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/order")
    public ResponseEntity<?> createOrder(@RequestBody PaymentRequest request) {
        try {
            if (!paymentService.isConfigured()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message", "Razorpay is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET."));
            }
            if (request.getAmount() <= 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "Amount must be greater than zero."));
            }

            Order order = paymentService.createOrder(request);
            paymentService.notifyPaymentInitiated(request, order);

            PaymentResponse resp = new PaymentResponse();
            resp.setOrderId(order.get("id"));
            resp.setAmount(order.get("amount"));
            resp.setCurrency(order.get("currency"));
            resp.setStatus(order.get("status"));
            resp.setKeyId(paymentService.getKeyId());
            resp.setCompanyName(paymentService.getCompanyName());
            resp.setReceipt(String.valueOf(order.get("receipt")));
            resp.setRegistrationId(request.getRegistrationId());
            resp.setTenantName(request.getTenantName());
            resp.setPaymentFor(request.getPaymentFor());
            resp.setMessage("Order created successfully.");
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage() == null ? "Failed to create order." : e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody PaymentRequest request) {
        try {
            if (!paymentService.isConfigured()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message", "Razorpay is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET."));
            }
            if (request.getRazorpayOrderId() == null || request.getRazorpayPaymentId() == null || request.getRazorpaySignature() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Verification payload is incomplete."));
            }

            boolean verified = paymentService.verifySignature(
                    request.getRazorpayOrderId(),
                    request.getRazorpayPaymentId(),
                    request.getRazorpaySignature()
            );

            if (!verified) {
                return ResponseEntity.badRequest().body(Map.of("message", "Payment signature verification failed."));
            }

            paymentService.notifyPaymentSuccess(request);

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
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage() == null ? "Failed to verify payment." : e.getMessage()));
        }
    }
}
