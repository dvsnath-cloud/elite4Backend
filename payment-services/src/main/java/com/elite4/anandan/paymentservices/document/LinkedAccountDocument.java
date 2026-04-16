package com.elite4.anandan.paymentservices.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "linked_accounts")
@CompoundIndex(name = "owner_colive_acct_idx", def = "{'ownerUsername': 1, 'coliveName': 1, 'accountNumber': 1}", unique = true)
public class LinkedAccountDocument {

    @Id
    private String id;

    @Indexed
    private String ownerUsername;
    private String coliveName;

    private String razorpayAccountId;
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

    // KYC / Onboarding fields (Phase 2)
    private String kycStatus;            // NOT_SUBMITTED, SUBMITTED, VERIFIED, FAILED
    private String productConfigStatus;  // NEEDS_CLARIFICATION, UNDER_REVIEW, ACTIVE, SUSPENDED
    private String stakeholderId;
    private String panNumber;
    private String gstNumber;
    private String businessAddress;
    private String activationStatus;     // NEW, ACTIVATED, SUSPENDED, UNDER_REVIEW

    // Document upload tracking (Phase 2 - KYC documents)
    private java.util.List<UploadedDocument> uploadedDocuments;

    // Razorpay sync tracking
    private boolean razorpaySynced;      // true = real Razorpay account, false = pending sync
    private String syncFailureReason;    // reason if Razorpay API call failed

    private boolean primary;         // only one per ownerUsername+coliveName
    private String status;           // CREATED, ACTIVE, SUSPENDED, PENDING_SYNC
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UploadedDocument {
        private String documentType;    // aadhar_front, aadhar_back, pan, passport_front, etc.
        private String razorpayDocId;   // Document ID returned by Razorpay
        private String originalFileName;
        private LocalDateTime uploadedAt;
    }
}
