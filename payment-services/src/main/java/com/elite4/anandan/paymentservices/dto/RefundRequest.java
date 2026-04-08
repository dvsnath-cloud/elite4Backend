package com.elite4.anandan.paymentservices.dto;

import lombok.Data;

@Data
public class RefundRequest {
    private String razorpayPaymentId;
    private int amount;            // in paise; 0 = full refund
    private String reason;
    private boolean reverseTransfer; // also reverse the route transfer
}
