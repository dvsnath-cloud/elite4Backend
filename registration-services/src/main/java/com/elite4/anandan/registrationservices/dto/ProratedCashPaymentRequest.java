package com.elite4.anandan.registrationservices.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for recording prorated/partial month cash payment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProratedCashPaymentRequest {
    private String tenantId;
    private String coliveName;
    private LocalDate proratedStartDate;        // First day tenant occupies room (e.g., 7th of month)
    private LocalDate proratedEndDate;          // Last day of month (usually month-end)
    private double proratedAmount;              // Amount for these days only
    private Integer numberOfDays;               // Number of days in prorated period
    private String receiptNumber;
    private LocalDate cashReceivedDate;
    private String remarks;
    private Double advanceAmount;               // Optional: Advance amount included in payment (default 0)
}
