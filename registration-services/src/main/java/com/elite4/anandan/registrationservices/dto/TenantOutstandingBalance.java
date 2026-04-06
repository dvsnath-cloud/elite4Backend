package com.elite4.anandan.registrationservices.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for tenant's outstanding balance summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantOutstandingBalance {
    private String tenantId;
    private String tenantName;
    private double totalOutstanding;            // Total unpaid across all months
    private double totalAdvanceUsed;            // Advance amount already adjusted
    private List<OutstandingMonth> monthlyBreakdown;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutstandingMonth {
        private String rentMonth;
        private String coliveName;
        private String roomNumber;
        private double expectedAmount;
        private double paidAmount;
        private double outstandingAmount;
        private String status;                  // PENDING, PARTIAL, OVERDUE
        private long daysOverdue;               // Number of days past due date
    }
}
