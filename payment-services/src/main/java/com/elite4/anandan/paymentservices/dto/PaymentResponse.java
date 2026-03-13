package com.elite4.anandan.paymentservices.dto;

import lombok.Data;

@Data
public class PaymentResponse {
    private String orderId;
    private int amount;
    private String currency;
    private String status;
}
