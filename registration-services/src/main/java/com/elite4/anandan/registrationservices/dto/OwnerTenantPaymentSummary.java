package com.elite4.anandan.registrationservices.dto;

import com.elite4.anandan.registrationservices.document.RentPaymentTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for owner's tenant payment summary in dashboard
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerTenantPaymentSummary {
    private String tenantId;
    private String tenantName;
    private String tenantPhone;
    private String roomNumber;
    private double expectedRent;
    private double paidAmount;
    private double remainingAmount;
    private double advanceAmount;
    private LocalDate dueDate;
    private LocalDate lastPaymentDate;
    private RentPaymentTransaction.PaymentStatus status;
    private String statusColor;  // For UI: green=PAID, yellow=PARTIAL, red=OVERDUE/UNPAID
    private String paymentMethod;
    
    public static OwnerTenantPaymentSummary from(RentPaymentTransaction transaction) {
        String statusColor;
        if (transaction.getStatus() == RentPaymentTransaction.PaymentStatus.COMPLETED) {
            statusColor = "#28a745"; // green
        } else if (transaction.getStatus() == RentPaymentTransaction.PaymentStatus.PARTIAL) {
            statusColor = "#ffc107"; // yellow
        } else if (transaction.getStatus() == RentPaymentTransaction.PaymentStatus.OVERDUE) {
            statusColor = "#dc3545"; // red
        } else {
            statusColor = "#6c757d"; // gray
        }
        
        return OwnerTenantPaymentSummary.builder()
            .tenantId(transaction.getTenantId())
            .tenantName(transaction.getTenantName())
            .tenantPhone(transaction.getTenantPhone())
            .roomNumber(transaction.getRoomNumber())
            .expectedRent(transaction.getRentAmount())
            .paidAmount(transaction.getPaidAmount())
            .remainingAmount(transaction.getRemainingAmount())
            .advanceAmount(transaction.getAdvanceAmount())
            .dueDate(transaction.getDueDate())
            .lastPaymentDate(transaction.getPaidDate())
            .status(transaction.getStatus())
            .statusColor(statusColor)
            .paymentMethod(transaction.getPaymentMethod())
            .build();
    }
}
