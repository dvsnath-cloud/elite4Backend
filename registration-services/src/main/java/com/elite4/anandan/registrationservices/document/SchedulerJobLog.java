package com.elite4.anandan.registrationservices.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Audit log for every scheduler job execution.
 * Tracks per-colive, per-tenant outcomes — payment record creation,
 * notification delivery, and any errors encountered.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "schedulerJobLogs")
public class SchedulerJobLog {

    @Id
    private String id;

    // ── Job identity ──
    @Indexed
    private String jobId;                       // Unique run ID (UUID)
    private String jobName;                     // e.g. "MONTHLY_RENT_GENERATION"
    @Indexed
    private LocalDate rentMonth;                // Which month this run is for (YYYY-MM-01)

    // ── Trigger info ──
    private TriggerType triggerType;            // SCHEDULED or MANUAL
    private String triggeredBy;                 // "SCHEDULER" or moderator username

    // ── Timing ──
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private long durationMs;                    // Total execution time in milliseconds

    // ── Overall status ──
    @Indexed
    private JobStatus status;                   // SUCCESS, PARTIAL_FAILURE, FAILURE
    private String errorMessage;                // Top-level error message if FAILURE

    // ── Aggregate counters ──
    private int totalActiveTenants;
    private int recordsCreated;
    private int recordsSkipped;                 // Already existed (idempotency)
    private int recordsFailed;
    private int notificationsSent;
    private int notificationsFailed;
    private int ownersSummaryNotified;

    // ── Per-colive breakdown ──
    private List<ColiveJobDetail> coliveDetails;

    // ── Per-tenant detail log ──
    private List<TenantJobDetail> tenantDetails;

    // ─────────────────────────── Enums ────────────────────────────

    public enum JobStatus {
        SUCCESS,            // All tenants processed without errors
        PARTIAL_FAILURE,    // Some tenants failed, some succeeded
        FAILURE             // Entire job failed (e.g. DB down)
    }

    public enum TriggerType {
        SCHEDULED,          // Ran via @Scheduled cron
        MANUAL              // Triggered via REST endpoint
    }

    public enum StepStatus {
        SUCCESS,
        FAILED,
        SKIPPED
    }

    // ─────────────────────── Inner classes ─────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColiveJobDetail {
        private String coliveName;
        private String ownerUsername;
        private int tenantsProcessed;
        private int recordsCreated;
        private int recordsSkipped;
        private int recordsFailed;
        private int notificationsSent;
        private int notificationsFailed;
        private double totalRentGenerated;      // Sum of rent amounts for records created
        private double totalPendingCarryForward; // Sum of pending balance carried forward
        private StepStatus ownerNotificationStatus;
        private String ownerNotificationError;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantJobDetail {
        private String tenantId;
        private String tenantName;
        private String coliveName;
        private String roomNumber;

        // Step 1: Create payment record
        private StepStatus paymentRecordStatus;
        private String paymentRecordId;         // ID of created RentPaymentTransaction
        private double rentAmount;
        private double pendingBalance;
        private String paymentRecordError;

        // Step 2: Tenant notification (email/SMS/WhatsApp)
        private StepStatus emailStatus;
        private String emailError;
        private StepStatus smsStatus;
        private String smsError;
        private StepStatus whatsappStatus;
        private String whatsappError;

        private String overallError;            // Catch-all error for this tenant
    }
}
