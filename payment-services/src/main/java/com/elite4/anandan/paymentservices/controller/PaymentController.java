package com.elite4.anandan.paymentservices.controller;

import com.elite4.anandan.paymentservices.dto.PaymentRequest;
import com.elite4.anandan.paymentservices.dto.PaymentResponse;
import com.elite4.anandan.paymentservices.service.PaymentService;
import com.razorpay.Order;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/order")
    public ResponseEntity<PaymentResponse> createOrder(@RequestBody PaymentRequest request) {
        try {
            Order order = paymentService.createOrder(request.getAmount(), request.getCurrency());
            PaymentResponse resp = new PaymentResponse();
            resp.setOrderId(order.get("id"));
            resp.setAmount(order.get("amount"));
            resp.setCurrency(order.get("currency"));
            resp.setStatus(order.get("status"));

            // notify user of successful order creation
            Map<String, Object> variables = new HashMap<>();
            variables.put("fname", "Customer"); // Assuming no name, can be extended
            variables.put("amount", resp.getAmount());
            variables.put("orderId", resp.getOrderId());
            if (request.getEmail() != null) {
                paymentService.notifyEmailWithTemplate(request.getEmail(), "Payment Initiated", "payment-success", variables);
            }
            if (request.getPhoneNumber() != null) {
                paymentService.notifySms(request.getPhoneNumber(), "Your payment order " + resp.getOrderId() + " for amount " + resp.getAmount() + " has been created.");
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (Exception e) {
            // Handle exception, perhaps return error response
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/success")
    public ResponseEntity<Void> paymentSuccess(@RequestBody PaymentRequest request) {
        // Simulate payment success notification
        Map<String, Object> variables = new HashMap<>();
        variables.put("fname", "Customer");
        variables.put("amount", request.getAmount());
        variables.put("orderId", "simulated-order-id");
        if (request.getEmail() != null) {
            paymentService.notifyEmailWithTemplate(request.getEmail(), "Payment Successful", "payment-success", variables);
        }
        if (request.getPhoneNumber() != null) {
            paymentService.notifySms(request.getPhoneNumber(), "Your payment has been processed successfully.");
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refund")
    public ResponseEntity<Void> paymentRefund(@RequestBody PaymentRequest request) {
        // Simulate refund notification
        String message = "Your refund for amount " + request.getAmount() + " has been processed.";
        if (request.getEmail() != null) {
            paymentService.notifyEmail(request.getEmail(), "Refund Processed", message);
        }
        if (request.getPhoneNumber() != null) {
            paymentService.notifySms(request.getPhoneNumber(), message);
        }
        return ResponseEntity.ok().build();
    }
}
