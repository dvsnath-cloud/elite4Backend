package com.elite4.anandan.registrationservices.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Consolidated DTO returning all payment context for a tenant in a single API call.
 * Replaces multiple frontend API calls and client-side computation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantPaymentSummary {

    // ── Tenant basics ──
    private String tenantId;
    private String tenantName;
    private String coliveName;
    private String roomNumber;
    private double roomRent;
    private double advanceAmount;
    private LocalDate checkInDate;

    // ── Current month status ──
    private boolean currentMonthPaid;
    private TenantPaymentHistoryItem currentMonthPayment; // null if not paid

    // ── Current month rent due (scheduler-generated PENDING record) ──
    private boolean rentDueGenerated;           // true when scheduler has created a PENDING record for this month
    private double currentMonthRentDue;          // rent amount due this month (from scheduler record)
    private double currentMonthPendingBalance;   // carried-forward pending from previous months
    private double currentMonthTotalDue;         // rentDue + pendingBalance
    private LocalDate currentMonthDueDate;       // payment due date
    private String currentMonthDueRemarks;       // scheduler remarks (e.g. pending balance info)

    // ── First-time payment detection ──
    private boolean firstTimePayment;

    // ── Prorated info (only relevant for first-time tenants who joined after 1st) ──
    private ProratedInfo proratedInfo; // null if not applicable

    // ── Outstanding balance ──
    private double totalOutstanding;
    private double totalAdvanceUsed;
    private List<TenantOutstandingBalance.OutstandingMonth> outstandingMonths;

    // ── Payment history (last 6 months) ──
    private List<TenantPaymentHistoryItem> paymentHistory;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProratedInfo {
        private LocalDate startDate;
        private LocalDate endDate;
        private int totalDaysInMonth;
        private int remainingDays;
        private double proratedAmount;
        private double fullRent;
        private String cycleLabel1st;   // label for 1st-to-1st cycle
        private String cycleLabelJoin;  // label for join-date cycle
    }
}
