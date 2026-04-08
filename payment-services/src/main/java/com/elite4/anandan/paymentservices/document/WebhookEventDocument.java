package com.elite4.anandan.paymentservices.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "webhook_events")
public class WebhookEventDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventId;
    private String eventType;         // payment.captured, payment.failed, transfer.settled, etc.

    private String razorpayPaymentId;
    private String razorpayOrderId;
    private int amount;
    private String currency;
    private String status;

    private String rawPayload;        // full JSON for audit
    private boolean processed;

    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
}
