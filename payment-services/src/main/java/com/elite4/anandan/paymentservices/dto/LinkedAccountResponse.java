package com.elite4.anandan.paymentservices.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LinkedAccountResponse {
    private String id;
    private String razorpayAccountId;
    private String ownerUsername;
    private String coliveName;
    private String bankName;
    private String ifscCode;
    private String accountNumber;
    private String beneficiaryName;
    private String upiId;
    private boolean primary;
    private String status;
    private String message;

    // KYC / Onboarding (Phase 2)
    private String kycStatus;
    private String productConfigStatus;
    private String activationStatus;
    private String panNumber;
    private String gstNumber;

    // Razorpay sync status
    private boolean razorpaySynced;
    private String syncFailureReason;

    // Document upload status
    private java.util.List<String> uploadedDocumentTypes;
}
