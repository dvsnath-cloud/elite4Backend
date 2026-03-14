package com.elite4.anandan.paymentservices.dto;

import lombok.Data;

@Data
public class PaymentRequest {
    private int amount; // in smallest currency unit, e.g. paise
    private String currency = "INR";
    private String email;
    private String phoneNumber;
}
