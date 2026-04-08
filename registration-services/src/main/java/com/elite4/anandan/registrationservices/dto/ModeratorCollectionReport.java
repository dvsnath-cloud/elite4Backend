package com.elite4.anandan.registrationservices.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO for moderator's real-time collection details and monthly report
 * Returns ROOM-BASED view showing payment status of each room for the selected month
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModeratorCollectionReport {
    private LocalDateTime reportGeneratedAt;
    private String moderatorUsername;
    private String coliveName;
    private String period;                      // e.g., "2026-04" for April 2026
    
    // Summary Totals for the month
    private int totalRooms;
    private int totalPaidRooms;
    private int totalUnpaidRooms;
    private int totalPartialRooms;
    
    private double totalExpectedRent;
    private double totalCollected;
    private double totalPending;
    
    // Room-wise payment details (MAIN DATA for UI display)
    private List<RoomPaymentStatus> rooms;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomPaymentStatus {
        private String registrationId;          // DB Reference
        private String roomNumber;
        private String tenantName;
        private String coliveUserName;          // Member onboarded user (if any)
        private double rentAmount;              // Monthly rent
        private double advanceAmount;           // Advance amount paid upfront
        private double paidAmount;              // Amount paid in this month
        private String status;                  // "PAID", "UNPAID", "PARTIAL"
        private LocalDate paidDate;             // Date of payment (null if unpaid)
        private String paymentMethod;           // "ONLINE", "CASH", "CHEQUE", null if unpaid
        private String paymentType;             // For reference: CASH or ONLINE
        private String receiptNumber;           // For cash payments
        private Boolean isProratedPayment;
        private Integer proratedDaysCount;
        private LocalDateTime transactionTime;  // Timestamp of payment
    }
}
