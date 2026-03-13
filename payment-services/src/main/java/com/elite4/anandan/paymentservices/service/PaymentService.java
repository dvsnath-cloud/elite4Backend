package com.elite4.anandan.paymentservices.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import java.util.Map;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final RazorpayClient razorpayClient;
    private final NotificationClient notificationClient;

    public PaymentService(@Value("${razorpay.keyId}") String keyId,
                          @Value("${razorpay.keySecret}") String keySecret,
                          NotificationClient notificationClient) throws Exception {
        this.razorpayClient = new RazorpayClient(keyId, keySecret);
        this.notificationClient = notificationClient;
    }

    /**
     * Creates an order in Razorpay and returns the resulting Order object.
     */
    public Order createOrder(int amount, String currency) throws Exception {
        JSONObject options = new JSONObject();
        options.put("amount", amount);
        options.put("currency", currency);
        options.put("receipt", "order_rcptid_" + System.currentTimeMillis());
        options.put("payment_capture", 1);
        return razorpayClient.orders.create(options);
    }

    public void notifyEmail(String to, String subject, String message) {
        notificationClient.sendEmail(to, subject, message);
    }

    public void notifyEmailWithTemplate(String to, String subject, String templateName, Map<String, Object> variables) {
        notificationClient.sendEmailWithTemplate(to, subject, templateName, variables);
    }

    public void notifySms(String phoneNumber, String message) {
        notificationClient.sendSms(phoneNumber, message);
    }

    public void notifySmsWithTemplate(String phoneNumber, String templateName, Map<String, Object> variables) {
        notificationClient.sendSmsWithTemplate(phoneNumber, templateName, variables);
    }
}
