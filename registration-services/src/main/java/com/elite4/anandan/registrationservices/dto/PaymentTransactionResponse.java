package com.elite4.anandan.registrationservices.dto;

import com.elite4.anandan.registrationservices.document.RentPaymentTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for rent payment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransactionResponse {
    private String id;
    private String tenantId;
    private String tenantName;
    private String tenantEmail;
    private String tenantPhone;
    private String coliveName;
    private String roomNumber;
    private String propertyAddress;
    
    private RentPaymentTransaction.PaymentType paymentType;
    private double rentAmount;
    private double paidAmount;
    private double remainingAmount;
    private LocalDate rentMonth;
    private LocalDate dueDate;
    private LocalDate paidDate;
    private RentPaymentTransaction.PaymentStatus status;
    private String paymentMethod;
    
    private String receiptNumber;
    private LocalDate cashReceivedDate;
    private String remarks;
    
    private String razorpayOrderId;
    private String razorpayPaymentId;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static PaymentTransactionResponse from(RentPaymentTransaction transaction) {
        return PaymentTransactionResponse.builder()
            .id(transaction.getId())
            .tenantId(transaction.getTenantId())
            .tenantName(transaction.getTenantName())
            .tenantEmail(transaction.getTenantEmail())
            .tenantPhone(transaction.getTenantPhone())
            .coliveName(transaction.getColiveName())
            .roomNumber(transaction.getRoomNumber())
            .propertyAddress(transaction.getPropertyAddress())
            .paymentType(transaction.getPaymentType())
            .rentAmount(transaction.getRentAmount())
            .paidAmount(transaction.getPaidAmount())
            .remainingAmount(transaction.getRemainingAmount())
            .rentMonth(transaction.getRentMonth())
            .dueDate(transaction.getDueDate())
            .paidDate(transaction.getPaidDate())
            .status(transaction.getStatus())
            .paymentMethod(transaction.getPaymentMethod())
            .receiptNumber(transaction.getReceiptNumber())
            .cashReceivedDate(transaction.getCashReceivedDate())
            .remarks(transaction.getRemarks())
            .razorpayOrderId(transaction.getRazorpayOrderId())
            .razorpayPaymentId(transaction.getRazorpayPaymentId())
            .createdAt(transaction.getCreatedAt())
            .updatedAt(transaction.getUpdatedAt())
            .build();
    }
}
