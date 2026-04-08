package com.elite4.anandan.paymentservices.dto;

import lombok.Data;

@Data
public class PaymentRequest {
    private int amount;
    private String currency = "INR";
    private String email;
    private String phoneNumber;
    private String receipt;
    private String registrationId;
    private String tenantName;
    private String paymentFor = "monthly_rent";
    private String description;
    private String ownerUsername;
    private String coliveName;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;
}
