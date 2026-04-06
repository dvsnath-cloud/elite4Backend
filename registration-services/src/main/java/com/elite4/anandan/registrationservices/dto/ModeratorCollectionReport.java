package com.elite4.anandan.registrationservices.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for moderator's real-time collection details and daily report
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModeratorCollectionReport {
    private LocalDateTime reportGeneratedAt;    // Current date and time
    private String moderatorUsername;
    private String coliveName;
    private String period;                      // e.g., "Today", "This Week", "This Month"
    
    // Summary Totals till now (today/current time)
    private int totalTransactionsProcessed;
    private double totalCashCollected;
    private double totalOnlineCollected;
    private double totalCollected;
    
    // Breakdown
    private int totalApprovedPayments;
    private int totalPendingApprovals;
    private int totalRejectedPayments;
    
    // Transaction-wise details
    private List<CollectionTransactionDetail> transactions;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectionTransactionDetail {
        private String transactionId;
        private String tenantName;
        private String roomNumber;
        private String coliveName;
        private double amount;
        private String paymentType;             // ONLINE, CASH
        private String paymentMethod;           // UPI, CREDIT_CARD, CASH, etc.
        private String rentMonth;
        private LocalDateTime collectionTime;   // Exact timestamp
        private String status;                  // COMPLETED, PENDING_APPROVAL, REJECTED
        private String receiptNumber;           // For cash payments
        private Boolean isProratedPayment;
        private Integer proratedDaysCount;
        private String approvalRemarks;         // If applicable
        private LocalDateTime approvedAt;
    }
}
