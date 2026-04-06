package com.elite4.anandan.registrationservices.dto;

import com.elite4.anandan.registrationservices.document.RentPaymentTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for pending cash payment approval for moderator view
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingPaymentApprovalItem {
    private String transactionId;
    private String tenantName;
    private String tenantPhone;
    private String roomNumber;
    private double rentAmount;
    private double paidAmount;
    private LocalDate rentMonth;
    private LocalDate cashReceivedDate;
    private String receiptNumber;
    private String paymentRemarks;
    private LocalDateTime submittedAt;
    private Boolean isProratedPayment;
    private LocalDate proratedStartDate;
    private LocalDate proratedEndDate;
    private Integer proratedDaysCount;
    
    public static PendingPaymentApprovalItem from(RentPaymentTransaction transaction) {
        return PendingPaymentApprovalItem.builder()
            .transactionId(transaction.getId())
            .tenantName(transaction.getTenantName())
            .tenantPhone(transaction.getTenantPhone())
            .roomNumber(transaction.getRoomNumber())
            .rentAmount(transaction.getRentAmount())
            .paidAmount(transaction.getPaidAmount())
            .rentMonth(transaction.getRentMonth())
            .cashReceivedDate(transaction.getCashReceivedDate())
            .receiptNumber(transaction.getReceiptNumber())
            .paymentRemarks(transaction.getRemarks())
            .submittedAt(transaction.getCreatedAt())
            .isProratedPayment(transaction.getIsProratedPayment())
            .proratedStartDate(transaction.getProratedStartDate())
            .proratedEndDate(transaction.getProratedEndDate())
            .proratedDaysCount(transaction.getProratedDaysCount())
            .build();
    }
}
