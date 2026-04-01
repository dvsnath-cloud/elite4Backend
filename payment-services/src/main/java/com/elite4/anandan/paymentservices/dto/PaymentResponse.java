package com.elite4.anandan.paymentservices.dto;

import lombok.Data;

@Data
public class PaymentResponse {
    private String orderId;
    private Integer amount;
    private String currency;
    private String status;
    private String keyId;
    private String companyName;
    private String receipt;
    private String registrationId;
    private String tenantName;
    private String paymentFor;
    private String paymentId;
    private Boolean verified;
    private String message;
    private String verifiedAt;
}
