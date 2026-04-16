package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.document.RegistrationDocument;
import com.elite4.anandan.registrationservices.document.RentPaymentTransaction;
import com.elite4.anandan.registrationservices.document.SchedulerJobLog;
import com.elite4.anandan.registrationservices.document.SchedulerJobLog.*;
import com.elite4.anandan.registrationservices.document.TenantJobDetailDocument;
import com.elite4.anandan.registrationservices.dto.Registration;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.RentPaymentTransactionRepository;
import com.elite4.anandan.registrationservices.repository.RegistrationRepository;
import com.elite4.anandan.registrationservices.repository.SchedulerJobLogRepository;
import com.elite4.anandan.registrationservices.repository.TenantJobDetailRepository;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import com.elite4.anandan.registrationservices.service.AsyncNotificationService.NotificationChannelResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Monthly rent scheduler — scales to 100K+ tenants.
 *
 * Key optimizations over the original implementation:
 * 1. Paginated tenant loading (configurable batch size, default 500)
 * 2. Batch pending-balance aggregation (1 MongoDB aggregation per batch vs N+1)
 * 3. Bulk record inserts via saveAll() (1 DB call per batch vs N)
 * 4. Async parallel notifications (20-thread pool, 3 channels fire concurrently per tenant)
 * 5. Per-tenant audit stored in separate collection (avoids 16MB BSON limit)
 * 6. RestTemplate timeouts (5s connect, 10s read) prevent hanging
 * 7. Dedicated scheduler thread pool (doesn't block other @Scheduled jobs)
 */
@Service
@Slf4j
public class RentSchedulerService {

    private final RentPaymentTransactionRepository paymentRepository;
    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final NotificationClient notificationClient;
    private final AsyncNotificationService asyncNotificationService;
    private final SchedulerJobLogRepository jobLogRepository;
    private final TenantJobDetailRepository tenantJobDetailRepository;

    @Value("${scheduler.batch.size:500}")
    private int batchSize;

    private static final List<RentPaymentTransaction.PaymentStatus> UNPAID_STATUSES = Arrays.asList(
            RentPaymentTransaction.PaymentStatus.PENDING,
            RentPaymentTransaction.PaymentStatus.PARTIAL,
            RentPaymentTransaction.PaymentStatus.OVERDUE
    );

    public RentSchedulerService(RentPaymentTransactionRepository paymentRepository,
                                 RegistrationRepository registrationRepository,
                                 UserRepository userRepository,
                                 NotificationClient notificationClient,
                                 AsyncNotificationService asyncNotificationService,
                                 SchedulerJobLogRepository jobLogRepository,
                                 TenantJobDetailRepository tenantJobDetailRepository) {
        this.paymentRepository = paymentRepository;
        this.registrationRepository = registrationRepository;
        this.userRepository = userRepository;
        this.notificationClient = notificationClient;
        this.asyncNotificationService = asyncNotificationService;
        this.jobLogRepository = jobLogRepository;
        this.tenantJobDetailRepository = tenantJobDetailRepository;
    }

    /**
     * Runs at midnight on the 1st of every month.
     */
    @Scheduled(cron = "0 0 0 1 * *")
    public void generateMonthlyRentRecords() {
        log.info("====== MONTHLY RENT SCHEDULER STARTED ======");
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        processMonthlyRent(currentMonth, TriggerType.SCHEDULED, "SCHEDULER");
        log.info("====== MONTHLY RENT SCHEDULER COMPLETED ======");
    }

    public Map<String, Object> processMonthlyRent(LocalDate rentMonth) {
        return processMonthlyRent(rentMonth, TriggerType.MANUAL, "MANUAL");
    }

    public Map<String, Object> processMonthlyRent(LocalDate rentMonth, TriggerType triggerType, String triggeredBy) {
        log.info("Processing monthly rent for: {} (trigger: {}, batchSize: {})", rentMonth, triggerType, batchSize);

        String jobId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();

        // Per-colive tracking
        Map<String, ColiveJobDetailBuilder> coliveBuilders = new LinkedHashMap<>();

        AtomicInteger created = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        AtomicInteger notified = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger notificationsFailed = new AtomicInteger(0);
        long totalTenantsProcessed = 0;
        String topLevelError = null;

        // Track per-owner summaries for owner notification
        Map<String, OwnerMonthlySummary> ownerSummaries = new HashMap<>();

        try {
            // 1. Get total count for progress logging
            long totalActive = registrationRepository.countByOccupied(Registration.roomOccupied.OCCUPIED);
            log.info("Total active tenants to process: {}", totalActive);

            // 2. Idempotency check — lightweight projection query
            List<RentPaymentTransaction> existingRecords = paymentRepository.findTenantIdsByRentMonth(rentMonth);
            Set<String> alreadyGenerated = existingRecords.stream()
                    .map(RentPaymentTransaction::getTenantId)
                    .collect(Collectors.toSet());
            log.info("Found {} existing records for month {} (will skip these)", alreadyGenerated.size(), rentMonth);

            // 3. Process tenants in batches
            int pageNumber = 0;
            Page<RegistrationDocument> batch;

            do {
                batch = registrationRepository.findByOccupied(
                        Registration.roomOccupied.OCCUPIED,
                        PageRequest.of(pageNumber, batchSize));

                List<RegistrationDocument> tenants = batch.getContent();
                if (tenants.isEmpty()) break;

                log.info("Processing batch {}/{} — {} tenants (page {} of {})",
                        pageNumber + 1, batch.getTotalPages(), tenants.size(),
                        pageNumber, batch.getTotalPages());

                processBatch(jobId, tenants, rentMonth, alreadyGenerated,
                        coliveBuilders, ownerSummaries,
                        created, skipped, notified, failed, notificationsFailed);

                totalTenantsProcessed += tenants.size();
                pageNumber++;

            } while (batch.hasNext());

            log.info("All batches processed. Total tenants: {}", totalTenantsProcessed);

            // 4. Send owner summaries
            sendOwnerSummaries(ownerSummaries, rentMonth, coliveBuilders);

        } catch (Exception e) {
            topLevelError = e.getMessage();
            log.error("CRITICAL: Scheduler job failed: {}", e.getMessage(), e);
        }

        // ── Build and persist the job log ──
        return persistJobLog(jobId, rentMonth, triggerType, triggeredBy, startedAt,
                coliveBuilders, totalTenantsProcessed,
                created.get(), skipped.get(), notified.get(), failed.get(),
                notificationsFailed.get(), topLevelError);
    }

    /**
     * Process one batch of tenants:
     * 1. Batch-compute pending balances (single aggregation)
     * 2. Build & bulk-insert PENDING records
     * 3. Fire async notifications for all tenants in the batch
     * 4. Await all notification futures and collect results
     * 5. Bulk-insert tenant audit details
     */
    private void processBatch(String jobId,
                               List<RegistrationDocument> tenants,
                               LocalDate rentMonth,
                               Set<String> alreadyGenerated,
                               Map<String, ColiveJobDetailBuilder> coliveBuilders,
                               Map<String, OwnerMonthlySummary> ownerSummaries,
                               AtomicInteger created, AtomicInteger skipped,
                               AtomicInteger notified, AtomicInteger failed,
                               AtomicInteger notificationsFailed) {

        // Separate tenants into skip vs. process
        List<RegistrationDocument> toProcess = new ArrayList<>();
        List<TenantJobDetailDocument> auditBatch = new ArrayList<>();

        for (RegistrationDocument tenant : tenants) {
            String coliveName = tenant.getColiveName() != null ? tenant.getColiveName() : "Unknown";
            String ownerUsername = tenant.getColiveUserName() != null ? tenant.getColiveUserName() : "Unknown";

            ColiveJobDetailBuilder coliveBuilder = coliveBuilders.computeIfAbsent(
                    coliveName, k -> new ColiveJobDetailBuilder(coliveName, ownerUsername));

            if (alreadyGenerated.contains(tenant.getId())) {
                skipped.incrementAndGet();
                coliveBuilder.recordSkipped();
                auditBatch.add(buildSkippedAudit(jobId, tenant, coliveName, "Already generated"));
                continue;
            }

            if (tenant.getRoomRent() <= 0) {
                skipped.incrementAndGet();
                coliveBuilder.recordSkipped();
                auditBatch.add(buildSkippedAudit(jobId, tenant, coliveName, "Room rent is 0 or not set"));
                continue;
            }

            toProcess.add(tenant);
        }

        if (toProcess.isEmpty()) {
            if (!auditBatch.isEmpty()) {
                tenantJobDetailRepository.saveAll(auditBatch);
            }
            return;
        }

        // 1. Batch pending balance — single aggregation query for all tenants in this batch
        Set<String> tenantIds = toProcess.stream()
                .map(RegistrationDocument::getId)
                .collect(Collectors.toSet());
        Map<String, Double> pendingBalances = paymentRepository.batchCalculatePendingBalances(tenantIds);

        // 2. Build PENDING records
        List<RentPaymentTransaction> recordsToInsert = new ArrayList<>();
        for (RegistrationDocument tenant : toProcess) {
            double pendingBalance = pendingBalances.getOrDefault(tenant.getId(), 0.0);
            recordsToInsert.add(createPendingRecord(tenant, rentMonth, pendingBalance));
        }

        // 3. Bulk insert — one DB call for entire batch
        List<RentPaymentTransaction> savedRecords;
        try {
            savedRecords = paymentRepository.saveAll(recordsToInsert);
            created.addAndGet(savedRecords.size());
        } catch (Exception e) {
            log.error("Bulk insert failed for batch — falling back to individual inserts: {}", e.getMessage());
            savedRecords = new ArrayList<>();
            for (RentPaymentTransaction record : recordsToInsert) {
                try {
                    savedRecords.add(paymentRepository.save(record));
                    created.incrementAndGet();
                } catch (Exception ex) {
                    failed.incrementAndGet();
                    log.error("Individual insert failed for tenant {}: {}", record.getTenantId(), ex.getMessage());
                    savedRecords.add(null); // placeholder to maintain index alignment
                }
            }
        }

        // 4. Map saved records back by tenantId for audit
        Map<String, RentPaymentTransaction> savedByTenantId = new HashMap<>();
        for (RentPaymentTransaction rec : savedRecords) {
            if (rec != null) {
                savedByTenantId.put(rec.getTenantId(), rec);
            }
        }

        // 5. Fire async notifications + build audit details
        String monthName = rentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        List<TenantNotificationFutures> futuresList = new ArrayList<>();

        for (RegistrationDocument tenant : toProcess) {
            String coliveName = tenant.getColiveName() != null ? tenant.getColiveName() : "Unknown";
            ColiveJobDetailBuilder coliveBuilder = coliveBuilders.get(coliveName);
            RentPaymentTransaction savedRec = savedByTenantId.get(tenant.getId());

            if (savedRec == null) {
                // Insert failed for this tenant
                failed.incrementAndGet();
                if (coliveBuilder != null) coliveBuilder.recordFailed();
                auditBatch.add(TenantJobDetailDocument.builder()
                        .jobId(jobId)
                        .tenantId(tenant.getId())
                        .tenantName(tenant.getFname() + " " + tenant.getLname())
                        .coliveName(coliveName)
                        .roomNumber(getRoomNumber(tenant))
                        .paymentRecordStatus(StepStatus.FAILED)
                        .paymentRecordError("Bulk insert failed")
                        .emailStatus(StepStatus.SKIPPED)
                        .smsStatus(StepStatus.SKIPPED)
                        .whatsappStatus(StepStatus.SKIPPED)
                        .build());
                continue;
            }

            double pendingBalance = pendingBalances.getOrDefault(tenant.getId(), 0.0);
            if (coliveBuilder != null) {
                coliveBuilder.recordCreated(tenant.getRoomRent(), pendingBalance);
            }

            // Accumulate owner summary
            if (tenant.getColiveUserName() != null) {
                ownerSummaries.computeIfAbsent(tenant.getColiveUserName(), k -> new OwnerMonthlySummary())
                        .addTenant(tenant, pendingBalance);
            }

            // Fire 3 async notifications
            double totalDue = tenant.getRoomRent() + pendingBalance;
            String message = buildTenantNotificationMessage(tenant, monthName, pendingBalance, totalDue);
            String subject = "Rent Due for " + monthName + " - CoLive Connect";

            CompletableFuture<NotificationChannelResult> emailFuture =
                    asyncNotificationService.sendEmailAsync(tenant.getId(), tenant.getEmail(), subject, message);
            CompletableFuture<NotificationChannelResult> smsFuture =
                    asyncNotificationService.sendSmsAsync(tenant.getId(), tenant.getContactNo(), message);
            CompletableFuture<NotificationChannelResult> whatsappFuture =
                    asyncNotificationService.sendWhatsappAsync(tenant.getId(), tenant.getContactNo(), message);

            futuresList.add(new TenantNotificationFutures(
                    tenant, savedRec, pendingBalance, emailFuture, smsFuture, whatsappFuture));
        }

        // 6. Await all notification futures and build audit entries
        for (TenantNotificationFutures tnf : futuresList) {
            String coliveName = tnf.tenant.getColiveName() != null ? tnf.tenant.getColiveName() : "Unknown";
            ColiveJobDetailBuilder coliveBuilder = coliveBuilders.get(coliveName);

            try {
                NotificationChannelResult emailResult = tnf.emailFuture.join();
                NotificationChannelResult smsResult = tnf.smsFuture.join();
                NotificationChannelResult whatsappResult = tnf.whatsappFuture.join();

                boolean anySuccess = emailResult.status() == StepStatus.SUCCESS
                        || smsResult.status() == StepStatus.SUCCESS
                        || whatsappResult.status() == StepStatus.SUCCESS;
                if (anySuccess) {
                    notified.incrementAndGet();
                    if (coliveBuilder != null) coliveBuilder.notificationSent();
                } else {
                    notificationsFailed.incrementAndGet();
                    if (coliveBuilder != null) coliveBuilder.notificationFailed();
                }

                auditBatch.add(TenantJobDetailDocument.builder()
                        .jobId(jobId)
                        .tenantId(tnf.tenant.getId())
                        .tenantName(tnf.tenant.getFname() + " " + tnf.tenant.getLname())
                        .coliveName(coliveName)
                        .roomNumber(getRoomNumber(tnf.tenant))
                        .paymentRecordStatus(StepStatus.SUCCESS)
                        .paymentRecordId(tnf.savedRecord.getId())
                        .rentAmount(tnf.tenant.getRoomRent())
                        .pendingBalance(tnf.pendingBalance)
                        .emailStatus(emailResult.status()).emailError(emailResult.error())
                        .smsStatus(smsResult.status()).smsError(smsResult.error())
                        .whatsappStatus(whatsappResult.status()).whatsappError(whatsappResult.error())
                        .build());

            } catch (Exception e) {
                log.error("Error collecting notification results for tenant {}: {}", tnf.tenant.getId(), e.getMessage());
                notificationsFailed.incrementAndGet();
                if (coliveBuilder != null) coliveBuilder.notificationFailed();

                auditBatch.add(TenantJobDetailDocument.builder()
                        .jobId(jobId)
                        .tenantId(tnf.tenant.getId())
                        .tenantName(tnf.tenant.getFname() + " " + tnf.tenant.getLname())
                        .coliveName(coliveName)
                        .roomNumber(getRoomNumber(tnf.tenant))
                        .paymentRecordStatus(StepStatus.SUCCESS)
                        .paymentRecordId(tnf.savedRecord.getId())
                        .rentAmount(tnf.tenant.getRoomRent())
                        .pendingBalance(tnf.pendingBalance)
                        .overallError("Notification collection error: " + e.getMessage())
                        .build());
            }
        }

        // 7. Bulk-insert audit details
        try {
            tenantJobDetailRepository.saveAll(auditBatch);
        } catch (Exception e) {
            log.error("Failed to persist tenant audit details for batch: {}", e.getMessage());
        }
    }

    private TenantJobDetailDocument buildSkippedAudit(String jobId, RegistrationDocument tenant,
                                                       String coliveName, String reason) {
        return TenantJobDetailDocument.builder()
                .jobId(jobId)
                .tenantId(tenant.getId())
                .tenantName(tenant.getFname() + " " + tenant.getLname())
                .coliveName(coliveName)
                .roomNumber(getRoomNumber(tenant))
                .paymentRecordStatus(StepStatus.SKIPPED)
                .paymentRecordError(reason)
                .emailStatus(StepStatus.SKIPPED)
                .smsStatus(StepStatus.SKIPPED)
                .whatsappStatus(StepStatus.SKIPPED)
                .build();
    }

    private String getRoomNumber(RegistrationDocument tenant) {
        return tenant.getRoomForRegistration() != null
                ? tenant.getRoomForRegistration().getRoomNumber() : "N/A";
    }

    /**
     * Send owner summaries and track status.
     */
    private void sendOwnerSummaries(Map<String, OwnerMonthlySummary> ownerSummaries,
                                     LocalDate rentMonth,
                                     Map<String, ColiveJobDetailBuilder> coliveBuilders) {
        log.info("Sending summaries to {} owners", ownerSummaries.size());
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
    }

    /**
     * Persist the job log and return the result summary.
     */
    private Map<String, Object> persistJobLog(String jobId, LocalDate rentMonth,
                                               TriggerType triggerType, String triggeredBy,
                                               LocalDateTime startedAt,
                                               Map<String, ColiveJobDetailBuilder> coliveBuilders,
                                               long totalTenantsProcessed,
                                               int created, int skipped, int notifiedCount,
                                               int failedCount, int notificationsFailedCount,
                                               String topLevelError) {
        LocalDateTime completedAt = LocalDateTime.now();
        long durationMs = java.time.Duration.between(startedAt, completedAt).toMillis();

        JobStatus jobStatus;
        if (topLevelError != null) {
            jobStatus = JobStatus.FAILURE;
        } else if (failedCount > 0) {
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
                .totalActiveTenants((int) totalTenantsProcessed)
                .recordsCreated(created)
                .recordsSkipped(skipped)
                .recordsFailed(failedCount)
                .notificationsSent(notifiedCount)
                .notificationsFailed(notificationsFailedCount)
                .ownersSummaryNotified(ownersSummaryNotified)
                .coliveDetails(coliveDetailsList)
                .tenantDetails(null)  // stored in separate collection
                .tenantDetailsCount(totalTenantsProcessed)
                .build();

        try {
            jobLogRepository.save(jobLog);
            log.info("Scheduler job log persisted — jobId={}, status={}, created={}, failed={}, duration={}ms",
                    jobId, jobStatus, created, failedCount, durationMs);
        } catch (Exception e) {
            log.error("Failed to persist scheduler job log: {}", e.getMessage(), e);
        }

        log.info("Monthly rent processing complete — created: {}, skipped: {}, notified: {}, failed: {}, duration: {}ms",
                created, skipped, notifiedCount, failedCount, durationMs);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", jobId);
        result.put("status", jobStatus.toString());
        result.put("month", rentMonth.toString());
        result.put("totalActiveTenants", totalTenantsProcessed);
        result.put("recordsCreated", created);
        result.put("recordsSkipped", skipped);
        result.put("notificationsSent", notifiedCount);
        result.put("failures", failedCount);
        result.put("ownersSummaryNotified", ownersSummaryNotified);
        result.put("durationMs", durationMs);
        return result;
    }

    /**
     * Create a PENDING rent payment record for the given tenant and month.
     */
    private RentPaymentTransaction createPendingRecord(RegistrationDocument tenant,
                                                        LocalDate rentMonth,
                                                        double pendingBalance) {
        return RentPaymentTransaction.builder()
                .tenantId(tenant.getId())
                .tenantName(tenant.getFname() + " " + tenant.getLname())
                .tenantEmail(tenant.getEmail())
                .tenantPhone(tenant.getContactNo())
                .coliveName(tenant.getColiveName())
                .coliveOwnerUsername(tenant.getColiveUserName())
                .roomNumber(getRoomNumber(tenant))
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

    private String buildTenantNotificationMessage(RegistrationDocument tenant,
                                                   String monthName,
                                                   double pendingBalance,
                                                   double totalDue) {
        String roomNumber = getRoomNumber(tenant);

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

        if (owner.getEmail() != null && !owner.getEmail().isBlank()) {
            notificationClient.sendEmail(owner.getEmail(), subject, message);
        }
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

    // ── Async notification futures holder ──

    private record TenantNotificationFutures(
            RegistrationDocument tenant,
            RentPaymentTransaction savedRecord,
            double pendingBalance,
            CompletableFuture<NotificationChannelResult> emailFuture,
            CompletableFuture<NotificationChannelResult> smsFuture,
            CompletableFuture<NotificationChannelResult> whatsappFuture) {}

    // ── Inner helper classes for owner summary aggregation ──

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
