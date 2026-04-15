package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.document.RegistrationDocument;
import com.elite4.anandan.registrationservices.document.RentPaymentTransaction;
import com.elite4.anandan.registrationservices.dto.Registration;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.RentPaymentTransactionRepository;
import com.elite4.anandan.registrationservices.repository.RegistrationRepository;
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
        processMonthlyRent(currentMonth);
        log.info("====== MONTHLY RENT SCHEDULER COMPLETED ======");
    }

    /**
     * Core logic — separated from @Scheduled so it can be triggered manually for any month.
     */
    public Map<String, Object> processMonthlyRent(LocalDate rentMonth) {
        log.info("Processing monthly rent for: {}", rentMonth);

        // 1. Get all active (OCCUPIED) tenants
        List<RegistrationDocument> activeTenants = registrationRepository
                .findByOccupied(Registration.roomOccupied.OCCUPIED);
        log.info("Found {} active tenants", activeTenants.size());

        // 2. Get existing payment records for this month (idempotency check)
        List<RentPaymentTransaction> existingRecords = paymentRepository.findByRentMonth(rentMonth);
        Set<String> alreadyGenerated = existingRecords.stream()
                .map(RentPaymentTransaction::getTenantId)
                .collect(Collectors.toSet());
        log.info("Found {} existing records for month {}", alreadyGenerated.size(), rentMonth);

        int created = 0;
        int skipped = 0;
        int notified = 0;
        int failed = 0;

        // Track per-owner summaries for owner notification
        Map<String, OwnerMonthlySummary> ownerSummaries = new HashMap<>();

        for (RegistrationDocument tenant : activeTenants) {
            try {
                if (alreadyGenerated.contains(tenant.getId())) {
                    log.debug("Skipping tenant {} — record already exists for {}", tenant.getId(), rentMonth);
                    skipped++;
                    continue;
                }

                // Skip tenants with no rent configured
                if (tenant.getRoomRent() <= 0) {
                    log.warn("Skipping tenant {} — roomRent is 0 or not set", tenant.getId());
                    skipped++;
                    continue;
                }

                // 3. Calculate pending balance from previous months
                double pendingBalance = calculatePendingBalance(tenant.getId());

                // 4. Create PENDING rent record
                RentPaymentTransaction record = createPendingRecord(tenant, rentMonth, pendingBalance);
                paymentRepository.save(record);
                created++;
                log.info("Created rent record for tenant {} ({}), rent=₹{}, pending=₹{}",
                        tenant.getFname() + " " + tenant.getLname(),
                        tenant.getId(), tenant.getRoomRent(), pendingBalance);

                // 5. Send notification to tenant
                sendTenantRentDueNotification(tenant, rentMonth, pendingBalance);
                notified++;

                // 6. Accumulate owner summary
                String ownerKey = tenant.getColiveUserName();
                if (ownerKey != null) {
                    ownerSummaries.computeIfAbsent(ownerKey, k -> new OwnerMonthlySummary())
                            .addTenant(tenant, pendingBalance);
                }

            } catch (Exception e) {
                failed++;
                log.error("Failed to process tenant {}: {}", tenant.getId(), e.getMessage(), e);
            }
        }

        // 7. Send summary notification to each property owner
        for (Map.Entry<String, OwnerMonthlySummary> entry : ownerSummaries.entrySet()) {
            try {
                sendOwnerMonthlySummaryNotification(entry.getKey(), rentMonth, entry.getValue());
            } catch (Exception e) {
                log.error("Failed to send summary to owner {}: {}", entry.getKey(), e.getMessage());
            }
        }

        log.info("Monthly rent processing complete — created: {}, skipped: {}, notified: {}, failed: {}",
                created, skipped, notified, failed);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", rentMonth.toString());
        result.put("totalActiveTenants", activeTenants.size());
        result.put("recordsCreated", created);
        result.put("recordsSkipped", skipped);
        result.put("notificationsSent", notified);
        result.put("failures", failed);
        result.put("ownersSummaryNotified", ownerSummaries.size());
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
        try {
            String monthName = rentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
            double totalDue = tenant.getRoomRent() + pendingBalance;

            String message = buildTenantNotificationMessage(tenant, monthName, pendingBalance, totalDue);
            String subject = "Rent Due for " + monthName + " - CoLive Connect";

            // Email
            if (tenant.getEmail() != null && !tenant.getEmail().isBlank()) {
                notificationClient.sendEmail(tenant.getEmail(), subject, message);
            }
            // SMS
            if (tenant.getContactNo() != null && !tenant.getContactNo().isBlank()) {
                notificationClient.sendSms(tenant.getContactNo(), message);
            }
            // WhatsApp
            if (tenant.getContactNo() != null && !tenant.getContactNo().isBlank()) {
                notificationClient.sendWhatsapp(tenant.getContactNo(), message);
            }

            log.debug("Sent rent-due notification to tenant {}", tenant.getId());
        } catch (Exception e) {
            log.warn("Failed to send notification to tenant {}: {}", tenant.getId(), e.getMessage());
        }
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
}
