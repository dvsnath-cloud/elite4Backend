package com.elite4.anandan.registrationservices.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for recording online rent payment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlinePaymentRequest {
    private String tenantId;
    private String coliveName;
    private LocalDate rentMonth;               // The month for which rent is being paid (YYYY-MM-01)
    private double amount;
    private String paymentMethod;              // UPI, CREDIT_CARD, DEBIT_CARD, etc.
    private String remarks;
    private Double advanceAmount;              // Optional: Advance amount included in payment (default 0)
}
