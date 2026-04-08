package com.elite4.anandan.paymentservices.dto;

import lombok.Data;

@Data
public class TransferRequest {
    private String paymentId;
    private String linkedAccountId;
    private int amount;
    private String currency = "INR";
    private String registrationId;
    private String tenantName;
    private String coliveName;
    private String ownerUsername;
}
