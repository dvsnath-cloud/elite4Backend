package com.elite4.anandan.registrationservices.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO for owner's payment dashboard (current month view)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerPaymentDashboard {
    private LocalDate month;                           // First day of the month (YYYY-MM-01)
    private String monthDisplay;                       // e.g., "April 2025"
    
    // Summary Totals for this month
    private int totalTenants;
    private double totalExpectedRent;
    private double totalReceivedRent;
    private double totalOutstandingRent;
    private int tenantsWithFullPayment;
    private int tenantsWithPartialPayment;
    private int tenantsWithNoPayment;
    
    // Breakdown by CoLive property
    private List<CoLivePropertyPaymentSummary> properties;
    
    // Tenant-wise payment details
    private List<OwnerTenantPaymentSummary> tenantPayments;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoLivePropertyPaymentSummary {
        private String coliveName;
        private String propertyAddress;
        private int totalRooms;
        private int occupiedRooms;
        private double expectedRent;
        private double receivedRent;
        private double outstandingRent;
        private int paidRooms;
        private int partialRooms;
        private int unpaidRooms;
    }
}
