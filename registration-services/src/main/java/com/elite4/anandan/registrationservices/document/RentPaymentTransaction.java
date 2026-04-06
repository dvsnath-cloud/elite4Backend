package com.elite4.anandan.registrationservices.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "rentPaymentTransactions")
public class RentPaymentTransaction {

    @Id
    private String id;

    // Tenant and Property Reference
    private String tenantId;                    // RegistrationDocument ID
    private String tenantName;
    private String tenantEmail;
    private String tenantPhone;

    // Owner/CoLive Reference
    private String coliveName;
    private String coliveOwnerUsername;        // User username
    private String coliveOwnerEmail;

    // Property/Room Reference
    private String roomId;                      // RoomOnBoardDocument ID
    private String roomNumber;
    private String propertyAddress;

    // Payment Details
    private PaymentType paymentType;            // ONLINE or CASH
    private double rentAmount;
    private double advanceAmount;
    private double paidAmount;
    private double remainingAmount;

    // Payment Period
    private LocalDate rentMonth;                // First day of the rent month (YYYY-MM-01)
    private LocalDate dueDate;
    private LocalDate paidDate;

    // Payment Status
    private PaymentStatus status;               // PENDING, COMPLETED, PARTIAL, OVERDUE, CANCELLED
    private String paymentMethod;               // UPI, CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, CASH

    // For Online Payments
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;

    // For Cash Payments
    private String receiptNumber;
    private LocalDate cashReceivedDate;

    // Notes and Remarks
    private String remarks;
    private String rejectionReason;             // If payment was rejected

    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;                   // admin, tenant, owner
    private String updatedBy;

    // Reference for partial payments
    private String previousTransactionId;       // If this is follow-up payment

    // Approval Workflow for Cash Payments (Requirement #2)
    private ApprovalStatus approvalStatus;      // PENDING_APPROVAL, APPROVED, REJECTED
    private String approvedBy;                  // Moderator/admin username
    private LocalDateTime approvedAt;
    private String approvalRemarks;

    // Prorated Payment Support (Requirement #3)
    private Boolean isProratedPayment;          // If this is a prorated/partial month payment
    private LocalDate proratedStartDate;        // First day tenant can occupy
    private LocalDate proratedEndDate;          // Last day of month (when prorated period ends)
    private Integer proratedDaysCount;          // Number of days in prorated period
    private double proratedAmount;              // Amount for prorated days
    private LocalDate nextBillingCycleStart;    // New cycle start date after proration

    // Collection Details (Requirements #5 #6)
    private String moderatorUsername;           // Moderator who collected/approved
    private LocalDateTime collectionDateTime;   // Exact time of collection/approval
    private String collectionDetails;           // Additional collection info

    public enum PaymentType {
        ONLINE,     // Through payment gateway (Razorpay)
        CASH        // Manual cash payment entry
    }

    public enum PaymentStatus {
        PENDING,                // Awaiting payment
        COMPLETED,              // Full payment received
        PARTIAL,                // Partial payment received
        OVERDUE,                // Past due date, not paid
        CANCELLED,              // Payment cancelled
        FAILED,                 // Payment failed
        PENDING_APPROVAL        // Awaits moderator approval
    }

    public enum ApprovalStatus {
        PENDING_APPROVAL,   // Awaiting moderator approval (for cash only)
        APPROVED,           // Approved by moderator
        REJECTED,           // Rejected by moderator
        NOT_REQUIRED        // For online payments, no approval needed
    }
}
