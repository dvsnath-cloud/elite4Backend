package com.elite4.anandan.registrationservices.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "transferRequests")
public class TransferRequestDocument {

    @Id
    private String id;

    // Tenant information
    private String tenantRegistrationId;
    private String tenantName;
    private String tenantContactNo;
    private String tenantEmail;

    // Source property details
    private String fromColiveUserName;
    private String fromColiveName;
    private String fromRoomNumber;
    private String fromHouseNumber;

    // Destination property details
    private String toColiveUserName;
    private String toColiveName;
    private String toRoomNumber;
    private String toHouseNumber;

    // Request metadata
    private String requestedBy;
    private String requestedByRole;
    private Date requestDate;
    private TransferStatus status;

    // Source (old colive) approval metadata
    private String sourceApprovedBy;
    private Date sourceApprovalDate;

    // Destination (new colive) approval metadata
    private String destinationApprovedBy;
    private Date destinationApprovalDate;

    // Rejection metadata
    private String rejectedBy;
    private Date rejectionDate;
    private String rejectionReason;

    // Completion metadata
    private String newRegistrationId;
    private Date completedDate;

    public enum TransferStatus {
        PENDING_SOURCE_APPROVAL,
        PENDING_DESTINATION_APPROVAL,
        COMPLETED,
        REJECTED
    }
}
