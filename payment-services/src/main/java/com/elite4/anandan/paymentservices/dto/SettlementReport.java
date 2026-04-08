package com.elite4.anandan.paymentservices.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SettlementReport {
    private String ownerUsername;
    private String coliveName;         // null for owner-level summaries
    private String periodLabel;        // e.g. "June 2025", "2025-06-01 to 2025-06-30"
    private int totalPayments;
    private long totalAmountCollected;  // paise
    private long totalPlatformFee;      // paise
    private long totalOwnerSettled;     // paise
    private int settledCount;
    private int pendingCount;
    private int failedCount;
    private List<TransferSummaryItem> transfers;

    @Data
    @Builder
    public static class TransferSummaryItem {
        private String transferId;
        private String tenantName;
        private String coliveName;
        private int totalAmount;
        private int platformFee;
        private int ownerAmount;
        private String status;
        private String paymentDate;
        private String settlementDate;
    }
}
