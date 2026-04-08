package com.elite4.anandan.paymentservices.dto;

import lombok.Data;

@Data
public class LinkedAccountRequest {
    private String ownerUsername;
    private String coliveName;
    private String contactName;
    private String email;
    private String phone;
    private String businessType;
    private String legalBusinessName;
    private String bankName;
    private String ifscCode;
    private String accountNumber;
    private String beneficiaryName;
    private String upiId;

    // KYC fields (Phase 2)
    private String panNumber;
    private String gstNumber;
    private String businessAddress;
}
