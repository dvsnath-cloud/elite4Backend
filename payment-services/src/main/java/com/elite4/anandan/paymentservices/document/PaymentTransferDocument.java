package com.elite4.anandan.paymentservices.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "payment_transfers")
public class PaymentTransferDocument {

    @Id
    private String id;

    @Indexed
    private String razorpayPaymentId;
    private String razorpayOrderId;
    private String razorpayTransferId;

    private String linkedAccountId;
    private String ownerUsername;
    private String coliveName;
    private String registrationId;
    private String tenantName;

    private int totalAmount;       // in paise
    private int platformFee;       // in paise (flat ₹49 = 4900)
    private int ownerAmount;       // totalAmount - platformFee

    private String status;         // CREATED, PROCESSED, SETTLED, FAILED
    private String failureReason;

    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private LocalDateTime settledAt;
}
