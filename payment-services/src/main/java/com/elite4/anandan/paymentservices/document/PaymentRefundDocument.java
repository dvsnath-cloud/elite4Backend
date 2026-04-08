package com.elite4.anandan.paymentservices.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "payment_refunds")
public class PaymentRefundDocument {

    @Id
    private String id;

    @Indexed
    private String razorpayPaymentId;
    private String razorpayRefundId;
    private String razorpayOrderId;

    private String ownerUsername;
    private String coliveName;
    private String tenantName;
    private String registrationId;

    private int originalAmount;     // paise
    private int refundAmount;       // paise
    private String reason;

    private boolean transferReversed;
    private String razorpayReversalId;

    private String status;          // INITIATED, PROCESSED, FAILED
    private String failureReason;

    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
}
