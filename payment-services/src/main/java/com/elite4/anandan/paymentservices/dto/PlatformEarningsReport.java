package com.elite4.anandan.paymentservices.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PlatformEarningsReport {
    private String periodLabel;
    private long totalPlatformEarnings;    // paise
    private long totalPaymentsProcessed;   // paise
    private int transactionCount;
    private List<OwnerSummary> ownerBreakdown;

    @Data
    @Builder
    public static class OwnerSummary {
        private String ownerUsername;
        private int transactionCount;
        private long totalCollected;       // paise
        private long platformFee;          // paise
        private long ownerSettled;         // paise
    }
}
