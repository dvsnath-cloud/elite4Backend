package com.elite4.anandan.paymentservices.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RefundResponse {
    private String refundId;
    private String razorpayRefundId;
    private String razorpayPaymentId;
    private int amount;
    private String status;
    private boolean transferReversed;
    private String message;
}
