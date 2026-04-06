package com.elite4.anandan.registrationservices.dto;

import com.elite4.anandan.registrationservices.document.RentPaymentTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for tenant payment history item (last 12 months)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantPaymentHistoryItem {
    private String transactionId;
    private LocalDate rentMonth;
    private double rentAmount;
    private double paidAmount;
    private double remainingAmount;
    private LocalDate dueDate;
    private LocalDate paidDate;
    private RentPaymentTransaction.PaymentStatus status;
    private String paymentMethod;
    private String paymentType;  // ONLINE or CASH
    private String coliveName;
    private String roomNumber;
    
    public static TenantPaymentHistoryItem from(RentPaymentTransaction transaction) {
        return TenantPaymentHistoryItem.builder()
            .transactionId(transaction.getId())
            .rentMonth(transaction.getRentMonth())
            .rentAmount(transaction.getRentAmount())
            .paidAmount(transaction.getPaidAmount())
            .remainingAmount(transaction.getRemainingAmount())
            .dueDate(transaction.getDueDate())
            .paidDate(transaction.getPaidDate())
            .status(transaction.getStatus())
            .paymentMethod(transaction.getPaymentMethod())
            .paymentType(transaction.getPaymentType() != null ? transaction.getPaymentType().toString() : "")
            .coliveName(transaction.getColiveName())
            .roomNumber(transaction.getRoomNumber())
            .build();
    }
}
