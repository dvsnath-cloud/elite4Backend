package com.elite4.anandan.registrationservices.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for recording cash rent payment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashPaymentRequest {
    private String tenantId;
    private String coliveName;
    private LocalDate rentMonth;               // The month for which rent is being paid (YYYY-MM-01)
    private double amount;                      // Total amount paid (rent + optional advance)
    private String receiptNumber;              // Receipt ID/number provided by tenant
    private LocalDate cashReceivedDate;        // When the cash was received
    private String remarks;
    private Double advanceAmount;              // Optional: Advance amount included in payment (default 0)
}
