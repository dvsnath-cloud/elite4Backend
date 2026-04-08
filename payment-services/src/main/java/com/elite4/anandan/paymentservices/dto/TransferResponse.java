package com.elite4.anandan.paymentservices.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransferResponse {
    private String transferId;
    private String razorpayTransferId;
    private String paymentId;
    private int amount;
    private int platformFee;
    private int ownerAmount;
    private String status;
    private String message;
}
