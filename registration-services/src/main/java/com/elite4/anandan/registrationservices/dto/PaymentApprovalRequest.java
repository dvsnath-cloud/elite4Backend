package com.elite4.anandan.registrationservices.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for moderator to approve/reject a cash payment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentApprovalRequest {
    private String transactionId;
    private Boolean approve;                    // true = approve, false = reject
    private String remarks;                     // Reasons for approval or rejection
}
