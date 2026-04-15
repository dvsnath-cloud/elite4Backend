package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.document.RegistrationDocument;
import com.elite4.anandan.registrationservices.document.RentPaymentTransaction;
import com.elite4.anandan.registrationservices.document.SchedulerJobLog;
import com.elite4.anandan.registrationservices.document.SchedulerJobLog.*;
import com.elite4.anandan.registrationservices.dto.Registration;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.RentPaymentTransactionRepository;
import com.elite4.anandan.registrationservices.repository.RegistrationRepository;
import com.elite4.anandan.registrationservices.repository.SchedulerJobLogRepository;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RentSchedulerService {

    private final RentPaymentTransactionRepository paymentRepository;
    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final NotificationClient notificationClient;
    private final SchedulerJobLogRepository jobLogRepository;

    private static final List<RentPaymentTransaction.PaymentStatus> UNPAID_STATUSES = Arrays.asList(
            RentPaymentTransaction.PaymentStatus.PENDING,
            RentPaymentTransaction.PaymentStatus.PARTIAL,
            RentPaymentTransaction.PaymentStatus.OVERDUE
    );

    /**
     * Runs at midnight on the 1st of every month.
     * 1. Generates PENDING rent payment records for all active tenants.
     * 2. Carries forward pending balance from previous months.
     * 3. Sends notification (email + SMS + WhatsApp) to each tenant about rent due + pending balance.
     * 4. Sends a summary notification to each property owner.
     */
    @Scheduled(cron = "0 0 0 1 * *")
    public void generateMonthlyRentRecords() {
        log.info("====== MONTHLY RENT SCHEDULER STARTED ======");
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        processMonthlyRent(currentMonth, TriggerType.SCHEDULED, "SCHEDULER");
        log.info("====== MONTHLY RENT SCHEDULER COMPLETED ======");
    }

    /**
     * Core logic — separated from @Scheduled so it can be triggered manually for any month.
     */
    public Map<String, Object> processMonthlyRent(LocalDate rentMonth) {
        return processMonthlyRent(rentMonth, TriggerType.MANUAL, "MANUAL");
    }

    public Map<String, Object> processMonthlyRent(LocalDate rentMonth, TriggerType triggerType, String triggeredBy) {
        log.info("Processing monthly rent for: {} (trigger: {})", rentMonth, triggerType);

        String jobId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();
        List<TenantJobDetail> tenantDetails = new ArrayList<>();

        // Per-colive tracking
        Map<String, ColiveJobDetailBuilder> coliveBuilders = new LinkedHashMap<>();

        int created = 0;
        int skipped = 0;
        int notified = 0;
        int failed = 0;
        int notificationsFailed = 0;
        String topLevelError = null;

        try {
            // 1. Get all active (OCCUPIED) tenants
            List<RegistrationDocument> activeTenants = registrationRepository
                    .findByOccupied(Registration.roomOccupied.OCCUPIED);
            log.info("Found {} active tenants", activeTenants.size());

            // 2. Idempotency check
            List<RentPaymentTransaction> existingRecords = paymentRepository.findByRentMonth(rentMonth);
            Set<String> alreadyGenerated = existingRecords.stream()
                    .map(RentPaymentTransaction::getTenantId)
                    .collect(java.util.stream.Collectors.toSet());
            log.info("Found {} existing records for month {}", alreadyGenerated.size(), rentMonth);

            // Track per-owner summaries for owner notification
            Map<String, OwnerMonthlySummary> ownerSummaries = new HashMap<>();

            for (RegistrationDocument tenant : activeTenants) {
                String coliveName = tenant.getColiveName() != null ? tenant.getColiveName() : "Unknown";
                String ownerUsername = tenant.getColiveUserName() != null ? tenant.getColiveUserName() : "Unknown";
                String roomNumber = tenant.getRoomForRegistration() != null
                        ? tenant.getRoomForRegistration().getRoomNumber() : "N/A";

                ColiveJobDetailBuilder coliveBuilder = coliveBuilders.computeIfAbsent(
                        coliveName, k -> new ColiveJobDetailBuilder(coliveName, ownerUsername));

                TenantJobDetail.TenantJobDetailBuilder td = TenantJobDetail.builder()
                        .tenantId(tenant.getId())
                        .tenantName(tenant.getFname() + " " + tenant.getLname())
                        .coliveName(coliveName)
                        .roomNumber(roomNumber);

                try {
                    // Skip if already generated
                    if (alreadyGenerated.contains(tenant.getId())) {
                        log.debug("Skipping tenant {} — record already exists for {}", tenant.getId(), rentMonth);
                        td.paymentRecordStatus(StepStatus.SKIPPED);
                        td.emailStatus(StepStatus.SKIPPED).smsStatus(StepStatus.SKIPPED).whatsappStatus(StepStatus.SKIPPED);
                        tenantDetails.add(td.build());
                        skipped++;
                        coliveBuilder.recordSkipped();
                        continue;
                    }

                    // Skip tenants with no rent
                    if (tenant.getRoomRent() <= 0) {
                        log.warn("Skipping tenant {} — roomRent is 0 or not set", tenant.getId());
                        td.paymentRecordStatus(StepStatus.SKIPPED)
                          .paymentRecordError("Room rent is 0 or not set");
                        td.emailStatus(StepStatus.SKIPPED).smsStatus(StepStatus.SKIPPED).whatsappStatus(StepStatus.SKIPPED);
                        tenantDetails.add(td.build());
                        skipped++;
                        coliveBuilder.recordSkipped();
                        continue;
                    }

                    // 3. Calculate pending balance
                    double pendingBalance = calculatePendingBalance(tenant.getId());
                    td.rentAmount(tenant.getRoomRent()).pendingBalance(pendingBalance);

                    // 4. Create PENDING rent record
                    RentPaymentTransaction record = createPendingRecord(tenant, rentMonth, pendingBalance);
                    record = paymentRepository.save(record);
                    td.paymentRecordStatus(StepStatus.SUCCESS).paymentRecordId(record.getId());
                    created++;
                    coliveBuilder.recordCreated(tenant.getRoomRent(), pendingBalance);
                    log.info("Created rent record for tenant {} ({}), rent=₹{}, pending=₹{}",
                            tenant.getFname() + " " + tenant.getLname(),
                            tenant.getId(), tenant.getRoomRent(), pendingBalance);

                    // 5. Send notifications (track each channel separately)
                    NotificationResult notifResult = sendTenantRentDueNotificationTracked(tenant, rentMonth, pendingBalance);
                    td.emailStatus(notifResult.emailStatus).emailError(notifResult.emailError);
                    td.smsStatus(notifResult.smsStatus).smsError(notifResult.smsError);
                    td.whatsappStatus(notifResult.whatsappStatus).whatsappError(notifResult.whatsappError);

                    boolean anyNotifSent = notifResult.emailStatus == StepStatus.SUCCESS
                            || notifResult.smsStatus == StepStatus.SUCCESS
                            || notifResult.whatsappStatus == StepStatus.SUCCESS;
                    if (anyNotifSent) {
                        notified++;
                        coliveBuilder.notificationSent();
                    } else {
                        notificationsFailed++;
                        coliveBuilder.notificationFailed();
                    }

                    // 6. Accumulate owner summary
                    if (tenant.getColiveUserName() != null) {
                        ownerSummaries.computeIfAbsent(tenant.getColiveUserName(), k -> new OwnerMonthlySummary())
                                .addTenant(tenant, pendingBalance);
                    }

                } catch (Exception e) {
                    failed++;
                    coliveBuilder.recordFailed();
                    td.paymentRecordStatus(td.build().getPaymentRecordStatus() != null
                            ? td.build().getPaymentRecordStatus() : StepStatus.FAILED);
                    td.overallError(e.getMessage());
                    log.error("Failed to process tenant {}: {}", tenant.getId(), e.getMessage(), e);
                }

                tenantDetails.add(td.build());
            }

            // 7. Send owner summaries and track status
            for (Map.Entry<String, OwnerMonthlySummary> entry : ownerSummaries.entrySet()) {
                ColiveJobDetailBuilder coliveBuilder = coliveBuilders.values().stream()
                        .filter(b -> entry.getKey().equals(b.ownerUsername))
                        .findFirst().orElse(null);
                try {
                    sendOwnerMonthlySummaryNotification(entry.getKey(), rentMonth, entry.getValue());
                    if (coliveBuilder != null) coliveBuilder.ownerNotifSuccess();
                } catch (Exception e) {
                    log.error("Failed to send summary to owner {}: {}", entry.getKey(), e.getMessage());
                    if (coliveBuilder != null) coliveBuilder.ownerNotifFailed(e.getMessage());
                }
            }

        } catch (Exception e) {
            topLevelError = e.getMessage();
            log.error("CRITICAL: Scheduler job failed: {}", e.getMessage(), e);
        }

        // ── Build and persist the job log ──
        LocalDateTime completedAt = LocalDateTime.now();
        long durationMs = java.time.Duration.between(startedAt, completedAt).toMillis();

        JobStatus jobStatus;
        if (topLevelError != null) {
            jobStatus = JobStatus.FAILURE;
        } else if (failed > 0) {
            jobStatus = JobStatus.PARTIAL_FAILURE;
        } else {
            jobStatus = JobStatus.SUCCESS;
        }

        List<ColiveJobDetail> coliveDetailsList = new ArrayList<>();
        int ownersSummaryNotified = 0;
        for (ColiveJobDetailBuilder b : coliveBuilders.values()) {
            ColiveJobDetail detail = b.build();
            coliveDetailsList.add(detail);
            if (detail.getOwnerNotificationStatus() == StepStatus.SUCCESS) ownersSummaryNotified++;
        }

        SchedulerJobLog jobLog = SchedulerJobLog.builder()
                .jobId(jobId)
                .jobName("MONTHLY_RENT_GENERATION")
                .rentMonth(rentMonth)
                .triggerType(triggerType)
                .triggeredBy(triggeredBy)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .durationMs(durationMs)
                .status(jobStatus)
                .errorMessage(topLevelError)
                .totalActiveTenants(tenantDetails.size())
                .recordsCreated(created)
                .recordsSkipped(skipped)
                .recordsFailed(failed)
                .notificationsSent(notified)
                .notificationsFailed(notificationsFailed)
                .ownersSummaryNotified(ownersSummaryNotified)
                .coliveDetails(coliveDetailsList)
                .tenantDetails(tenantDetails)
                .build();

        try {
            jobLogRepository.save(jobLog);
            log.info("Scheduler job log persisted — jobId={}, status={}, created={}, failed={}",
                    jobId, jobStatus, created, failed);
        } catch (Exception e) {
            log.error("Failed to persist scheduler job log: {}", e.getMessage(), e);
        }

        log.info("Monthly rent processing complete — created: {}, skipped: {}, notified: {}, failed: {}",
                created, skipped, notified, failed);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", jobId);
        result.put("status", jobStatus.toString());
        result.put("month", rentMonth.toString());
        result.put("totalActiveTenants", tenantDetails.size());
        result.put("recordsCreated", created);
        result.put("recordsSkipped", skipped);
        result.put("notificationsSent", notified);
        result.put("failures", failed);
        result.put("ownersSummaryNotified", ownersSummaryNotified);
        result.put("durationMs", durationMs);
        return result;
    }

    /**
     * Calculate total pending balance from all previous unpaid/partial months.
     */
    private double calculatePendingBalance(String tenantId) {
        List<RentPaymentTransaction> unpaid = paymentRepository
                .findByTenantIdAndStatusIn(tenantId, UNPAID_STATUSES);
        return unpaid.stream()
                .mapToDouble(RentPaymentTransaction::getRemainingAmount)
                .sum();
    }

    /**
     * Create a PENDING rent payment record for the given tenant and month.
     */
    private RentPaymentTransaction createPendingRecord(RegistrationDocument tenant,
                                                        LocalDate rentMonth,
                                                        double pendingBalance) {
        String roomNumber = tenant.getRoomForRegistration() != null
                ? tenant.getRoomForRegistration().getRoomNumber() : "N/A";

        return RentPaymentTransaction.builder()
                .tenantId(tenant.getId())
                .tenantName(tenant.getFname() + " " + tenant.getLname())
                .tenantEmail(tenant.getEmail())
                .tenantPhone(tenant.getContactNo())
                .coliveName(tenant.getColiveName())
                .coliveOwnerUsername(tenant.getColiveUserName())
                .roomNumber(roomNumber)
                .propertyAddress(tenant.getAddress())
                .rentAmount(tenant.getRoomRent())
                .advanceAmount(0)
                .paidAmount(0)
                .remainingAmount(tenant.getRoomRent())
                .rentMonth(rentMonth)
                .dueDate(rentMonth.plusMonths(1).minusDays(1))
                .status(RentPaymentTransaction.PaymentStatus.PENDING)
                .approvalStatus(RentPaymentTransaction.ApprovalStatus.NOT_REQUIRED)
                .remarks(pendingBalance > 0
                        ? String.format("Auto-generated. Pending balance from previous months: ₹%.2f", pendingBalance)
                        : "Auto-generated monthly rent record")
                .createdBy("SCHEDULER")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Send rent-due notification to tenant via email, SMS, and WhatsApp.
     */
    private void sendTenantRentDueNotification(RegistrationDocument tenant,
                                                LocalDate rentMonth,
                                                double pendingBalance) {
        sendTenantRentDueNotificationTracked(tenant, rentMonth, pendingBalance);
    }

    /**
     * Send rent-due notification per channel and return tracked result.
     */
    private NotificationResult sendTenantRentDueNotificationTracked(RegistrationDocument tenant,
                                                                     LocalDate rentMonth,
                                                                     double pendingBalance) {
        NotificationResult result = new NotificationResult();
        try {
            String monthName = rentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
            double totalDue = tenant.getRoomRent() + pendingBalance;
            String message = buildTenantNotificationMessage(tenant, monthName, pendingBalance, totalDue);
            String subject = "Rent Due for " + monthName + " - CoLive Connect";

            // Email
            if (tenant.getEmail() != null && !tenant.getEmail().isBlank()) {
                try {
                    notificationClient.sendEmail(tenant.getEmail(), subject, message);
                    result.emailStatus = StepStatus.SUCCESS;
                } catch (Exception e) {
                    result.emailStatus = StepStatus.FAILED;
                    result.emailError = e.getMessage();
                    log.warn("Email failed for tenant {}: {}", tenant.getId(), e.getMessage());
                }
            } else {
                result.emailStatus = StepStatus.SKIPPED;
                result.emailError = "No email configured";
            }

            // SMS
            if (tenant.getContactNo() != null && !tenant.getContactNo().isBlank()) {
                try {
                    notificationClient.sendSms(tenant.getContactNo(), message);
                    result.smsStatus = StepStatus.SUCCESS;
                } catch (Exception e) {
                    result.smsStatus = StepStatus.FAILED;
                    result.smsError = e.getMessage();
                    log.warn("SMS failed for tenant {}: {}", tenant.getId(), e.getMessage());
                }
            } else {
                result.smsStatus = StepStatus.SKIPPED;
                result.smsError = "No contact number configured";
            }

            // WhatsApp
            if (tenant.getContactNo() != null && !tenant.getContactNo().isBlank()) {
                try {
                    notificationClient.sendWhatsapp(tenant.getContactNo(), message);
                    result.whatsappStatus = StepStatus.SUCCESS;
                } catch (Exception e) {
                    result.whatsappStatus = StepStatus.FAILED;
                    result.whatsappError = e.getMessage();
                    log.warn("WhatsApp failed for tenant {}: {}", tenant.getId(), e.getMessage());
                }
            } else {
                result.whatsappStatus = StepStatus.SKIPPED;
                result.whatsappError = "No contact number configured";
            }

            log.debug("Notification result for tenant {}: email={}, sms={}, whatsapp={}",
                    tenant.getId(), result.emailStatus, result.smsStatus, result.whatsappStatus);
        } catch (Exception e) {
            log.warn("Failed to send notification to tenant {}: {}", tenant.getId(), e.getMessage());
            if (result.emailStatus == null) result.emailStatus = StepStatus.FAILED;
            if (result.smsStatus == null) result.smsStatus = StepStatus.FAILED;
            if (result.whatsappStatus == null) result.whatsappStatus = StepStatus.FAILED;
        }
        return result;
    }

    /** Holds per-channel notification result */
    private static class NotificationResult {
        StepStatus emailStatus;
        String emailError;
        StepStatus smsStatus;
        String smsError;
        StepStatus whatsappStatus;
        String whatsappError;
    }

    private String buildTenantNotificationMessage(RegistrationDocument tenant,
                                                   String monthName,
                                                   double pendingBalance,
                                                   double totalDue) {
        String roomNumber = tenant.getRoomForRegistration() != null
                ? tenant.getRoomForRegistration().getRoomNumber() : "N/A";

        StringBuilder sb = new StringBuilder();
        sb.append("Dear ").append(tenant.getFname()).append(",\n\n");
        sb.append("Your rent for ").append(monthName).append(" is now due.\n\n");
        sb.append("Property: ").append(tenant.getColiveName()).append("\n");
        sb.append("Room: ").append(roomNumber).append("\n");
        sb.append("Monthly Rent: ₹").append(String.format("%.2f", tenant.getRoomRent())).append("\n");

        if (pendingBalance > 0) {
            sb.append("Pending Balance (previous months): ₹").append(String.format("%.2f", pendingBalance)).append("\n");
            sb.append("Total Amount Due: ₹").append(String.format("%.2f", totalDue)).append("\n");
        }

        sb.append("\nPlease make the payment at your earliest convenience.\n");
        sb.append("Thank you,\nCoLive Connect");
        return sb.toString();
    }

    /**
     * Send a monthly summary to the property owner covering all their tenants.
     */
    private void sendOwnerMonthlySummaryNotification(String ownerUsername,
                                                      LocalDate rentMonth,
                                                      OwnerMonthlySummary summary) {
        Optional<User> ownerOpt = userRepository.findByUsername(ownerUsername);
        if (ownerOpt.isEmpty()) {
            log.warn("Owner not found for username: {}", ownerUsername);
            return;
        }

        User owner = ownerOpt.get();
        String monthName = rentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"));

        String subject = "Monthly Rent Summary for " + monthName + " - CoLive Connect";
        String message = buildOwnerSummaryMessage(monthName, summary);

        // Email
        if (owner.getEmail() != null && !owner.getEmail().isBlank()) {
            notificationClient.sendEmail(owner.getEmail(), subject, message);
        }
        // SMS (shorter version)
        if (owner.getPhoneRaw() != null && !owner.getPhoneRaw().isBlank()) {
            String smsMessage = String.format(
                    "CoLive Connect: %s rent summary — %d tenants, Expected: ₹%.0f, Pending from prev months: ₹%.0f, Total Outstanding: ₹%.0f",
                    monthName, summary.tenantCount, summary.totalExpectedRent,
                    summary.totalPendingBalance, summary.totalExpectedRent + summary.totalPendingBalance);
            notificationClient.sendSms(owner.getPhoneRaw(), smsMessage);
            notificationClient.sendWhatsapp(owner.getPhoneRaw(), message);
        }

        log.info("Sent monthly summary to owner {}: {} tenants, expected ₹{}, pending ₹{}",
                ownerUsername, summary.tenantCount, summary.totalExpectedRent, summary.totalPendingBalance);
    }

    private String buildOwnerSummaryMessage(String monthName, OwnerMonthlySummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("Monthly Rent Summary for ").append(monthName).append("\n\n");
        sb.append("Total Active Tenants: ").append(summary.tenantCount).append("\n");
        sb.append("Expected Rent (this month): ₹").append(String.format("%.2f", summary.totalExpectedRent)).append("\n");

        if (summary.totalPendingBalance > 0) {
            sb.append("Pending Balance (previous months): ₹")
                    .append(String.format("%.2f", summary.totalPendingBalance)).append("\n");
            sb.append("Total Outstanding: ₹")
                    .append(String.format("%.2f", summary.totalExpectedRent + summary.totalPendingBalance)).append("\n");
        }

        // Per-property breakdown
        if (!summary.propertyBreakdown.isEmpty()) {
            sb.append("\n--- Property Breakdown ---\n");
            for (Map.Entry<String, PropertySummary> entry : summary.propertyBreakdown.entrySet()) {
                PropertySummary ps = entry.getValue();
                sb.append("\n").append(entry.getKey()).append(":\n");
                sb.append("  Tenants: ").append(ps.tenantCount).append("\n");
                sb.append("  Expected: ₹").append(String.format("%.2f", ps.expectedRent)).append("\n");
                if (ps.pendingBalance > 0) {
                    sb.append("  Pending: ₹").append(String.format("%.2f", ps.pendingBalance)).append("\n");
                }
            }
        }

        // Tenants with pending balance
        if (!summary.tenantsWithPending.isEmpty()) {
            sb.append("\n--- Tenants with Pending Balance ---\n");
            for (TenantPendingInfo info : summary.tenantsWithPending) {
                sb.append("  ").append(info.name).append(" (Room ").append(info.roomNumber)
                        .append("): ₹").append(String.format("%.2f", info.pendingAmount)).append("\n");
            }
        }

        sb.append("\n— CoLive Connect");
        return sb.toString();
    }

    // ---- Inner helper classes for owner summary aggregation ----

    private static class OwnerMonthlySummary {
        int tenantCount = 0;
        double totalExpectedRent = 0;
        double totalPendingBalance = 0;
        Map<String, PropertySummary> propertyBreakdown = new LinkedHashMap<>();
        List<TenantPendingInfo> tenantsWithPending = new ArrayList<>();

        void addTenant(RegistrationDocument tenant, double pendingBalance) {
            tenantCount++;
            totalExpectedRent += tenant.getRoomRent();
            totalPendingBalance += pendingBalance;

            String property = tenant.getColiveName() != null ? tenant.getColiveName() : "Unknown";
            propertyBreakdown.computeIfAbsent(property, k -> new PropertySummary())
                    .add(tenant.getRoomRent(), pendingBalance);

            if (pendingBalance > 0) {
                String roomNumber = tenant.getRoomForRegistration() != null
                        ? tenant.getRoomForRegistration().getRoomNumber() : "N/A";
                tenantsWithPending.add(new TenantPendingInfo(
                        tenant.getFname() + " " + tenant.getLname(),
                        roomNumber,
                        pendingBalance
                ));
            }
        }
    }

    private static class PropertySummary {
        int tenantCount = 0;
        double expectedRent = 0;
        double pendingBalance = 0;

        void add(double rent, double pending) {
            tenantCount++;
            expectedRent += rent;
            pendingBalance += pending;
        }
    }

    private static class TenantPendingInfo {
        String name;
        String roomNumber;
        double pendingAmount;

        TenantPendingInfo(String name, String roomNumber, double pendingAmount) {
            this.name = name;
            this.roomNumber = roomNumber;
            this.pendingAmount = pendingAmount;
        }
    }

    /** Mutable builder for per-colive aggregation during job execution */
    private static class ColiveJobDetailBuilder {
        String coliveName;
        String ownerUsername;
        int tenantsProcessed = 0;
        int recordsCreated = 0;
        int recordsSkipped = 0;
        int recordsFailed = 0;
        int notificationsSent = 0;
        int notificationsFailed = 0;
        double totalRentGenerated = 0;
        double totalPendingCarryForward = 0;
        StepStatus ownerNotificationStatus;
        String ownerNotificationError;

        ColiveJobDetailBuilder(String coliveName, String ownerUsername) {
            this.coliveName = coliveName;
            this.ownerUsername = ownerUsername;
        }

        void recordCreated(double rent, double pending) {
            tenantsProcessed++;
            recordsCreated++;
            totalRentGenerated += rent;
            totalPendingCarryForward += pending;
        }

        void recordSkipped() {
            tenantsProcessed++;
            recordsSkipped++;
        }

        void recordFailed() {
            tenantsProcessed++;
            recordsFailed++;
        }

        void notificationSent() { notificationsSent++; }
        void notificationFailed() { notificationsFailed++; }

        void ownerNotifSuccess() { ownerNotificationStatus = StepStatus.SUCCESS; }
        void ownerNotifFailed(String error) {
            ownerNotificationStatus = StepStatus.FAILED;
            ownerNotificationError = error;
        }

        ColiveJobDetail build() {
            return ColiveJobDetail.builder()
                    .coliveName(coliveName)
                    .ownerUsername(ownerUsername)
                    .tenantsProcessed(tenantsProcessed)
                    .recordsCreated(recordsCreated)
                    .recordsSkipped(recordsSkipped)
                    .recordsFailed(recordsFailed)
                    .notificationsSent(notificationsSent)
                    .notificationsFailed(notificationsFailed)
                    .totalRentGenerated(totalRentGenerated)
                    .totalPendingCarryForward(totalPendingCarryForward)
                    .ownerNotificationStatus(ownerNotificationStatus)
                    .ownerNotificationError(ownerNotificationError)
                    .build();
        }
    }
}
