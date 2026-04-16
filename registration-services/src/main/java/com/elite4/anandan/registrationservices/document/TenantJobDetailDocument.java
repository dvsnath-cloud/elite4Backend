package com.elite4.anandan.registrationservices.document;

import com.elite4.anandan.registrationservices.document.SchedulerJobLog.StepStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Separate collection for per-tenant scheduler job audit details.
 * Moved out of SchedulerJobLog to avoid the 16MB BSON document limit
 * when processing 100K+ tenants.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "schedulerTenantDetails")
@CompoundIndex(name = "idx_jobId_tenantId", def = "{'jobId': 1, 'tenantId': 1}")
public class TenantJobDetailDocument {

    @Id
    private String id;

    @Indexed
    private String jobId;

    private String tenantId;
    private String tenantName;
    private String coliveName;
    private String roomNumber;

    // Payment record creation
    private StepStatus paymentRecordStatus;
    private String paymentRecordId;
    private double rentAmount;
    private double pendingBalance;
    private String paymentRecordError;

    // Notification statuses
    private StepStatus emailStatus;
    private String emailError;
    private StepStatus smsStatus;
    private String smsError;
    private StepStatus whatsappStatus;
    private String whatsappError;

    private String overallError;
}
