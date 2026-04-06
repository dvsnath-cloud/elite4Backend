package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.document.RegistrationDocument;
import com.elite4.anandan.registrationservices.document.RentPaymentTransaction;
import com.elite4.anandan.registrationservices.dto.*;
import com.elite4.anandan.registrationservices.repository.RentPaymentTransactionRepository;
import com.elite4.anandan.registrationservices.repository.RegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RentPaymentService {

    private final RentPaymentTransactionRepository paymentRepository;
    private final RegistrationRepository registrationRepository;

    /**
     * Record a cash payment for rent
     */
    public PaymentTransactionResponse recordCashPayment(CashPaymentRequest request, String currentUsername) {
        log.info("Recording cash payment for tenant: {} for month: {}", request.getTenantId(), request.getRentMonth());

        // Get tenant details
        Optional<RegistrationDocument> tenant = registrationRepository.findById(request.getTenantId());
        if (tenant.isEmpty()) {
            throw new IllegalArgumentException("Tenant not found with ID: " + request.getTenantId());
        }

        RegistrationDocument registration = tenant.get();

        // Check if payment already exists for this month
        Optional<RentPaymentTransaction> existingPayment = 
            paymentRepository.findByTenantIdAndRentMonth(request.getTenantId(), request.getRentMonth());

        RentPaymentTransaction transaction = existingPayment.orElse(new RentPaymentTransaction());

        // Calculate payment status
        double rentAmount = registration.getRoomRent();
        double remainingAmount = Math.max(0, rentAmount - request.getAmount());
        RentPaymentTransaction.PaymentStatus status = request.getAmount() >= rentAmount ? 
            RentPaymentTransaction.PaymentStatus.COMPLETED : 
            RentPaymentTransaction.PaymentStatus.PARTIAL;

        // Populate transaction
        transaction.setTenantId(registration.getId());
        transaction.setTenantName(registration.getFname() + " " + registration.getLname());
        transaction.setTenantEmail(registration.getEmail());
        transaction.setTenantPhone(registration.getContactNo());
        transaction.setColiveName(registration.getColiveName());
        transaction.setColiveOwnerUsername(registration.getColiveUserName());
        transaction.setRoomNumber(registration.getRoomForRegistration() != null ? 
            registration.getRoomForRegistration().getRoomNumber() : "N/A");
        transaction.setPropertyAddress(registration.getAddress());

        transaction.setPaymentType(RentPaymentTransaction.PaymentType.CASH);
        transaction.setRentAmount(rentAmount);
        transaction.setAdvanceAmount(registration.getAdvanceAmount());
        transaction.setPaidAmount(request.getAmount());
        transaction.setRemainingAmount(remainingAmount);
        transaction.setRentMonth(request.getRentMonth());
        transaction.setDueDate(request.getRentMonth().plusMonths(1).minusDays(1)); // Last day of month
        transaction.setPaidDate(request.getCashReceivedDate());
        transaction.setStatus(status);
        transaction.setPaymentMethod("CASH");

        transaction.setReceiptNumber(request.getReceiptNumber());
        transaction.setCashReceivedDate(request.getCashReceivedDate());
        transaction.setRemarks(request.getRemarks());

        transaction.setCreatedBy(currentUsername);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());
        transaction.setUpdatedBy(currentUsername);

        RentPaymentTransaction saved = paymentRepository.save(transaction);
        log.info("Cash payment recorded successfully: {}", saved.getId());

        return PaymentTransactionResponse.from(saved);
    }

    /**
     * Record an online payment
     */
    public PaymentTransactionResponse recordOnlinePayment(OnlinePaymentRequest request, String currentUsername) {
        log.info("Recording online payment for tenant: {} for month: {}", request.getTenantId(), request.getRentMonth());

        // Get tenant details
        Optional<RegistrationDocument> tenant = registrationRepository.findById(request.getTenantId());
        if (tenant.isEmpty()) {
            throw new IllegalArgumentException("Tenant not found with ID: " + request.getTenantId());
        }

        RegistrationDocument registration = tenant.get();

        // Check if payment already exists for this month
        Optional<RentPaymentTransaction> existingPayment = 
            paymentRepository.findByTenantIdAndRentMonth(request.getTenantId(), request.getRentMonth());

        RentPaymentTransaction transaction = existingPayment.orElse(new RentPaymentTransaction());

        // Calculate payment status
        double rentAmount = registration.getRoomRent();
        double remainingAmount = Math.max(0, rentAmount - request.getAmount());
        RentPaymentTransaction.PaymentStatus status = request.getAmount() >= rentAmount ? 
            RentPaymentTransaction.PaymentStatus.COMPLETED : 
            RentPaymentTransaction.PaymentStatus.PARTIAL;

        // Populate transaction
        transaction.setTenantId(registration.getId());
        transaction.setTenantName(registration.getFname() + " " + registration.getLname());
        transaction.setTenantEmail(registration.getEmail());
        transaction.setTenantPhone(registration.getContactNo());
        transaction.setColiveName(registration.getColiveName());
        transaction.setColiveOwnerUsername(registration.getColiveUserName());
        transaction.setRoomNumber(registration.getRoomForRegistration() != null ? 
            registration.getRoomForRegistration().getRoomNumber() : "N/A");
        transaction.setPropertyAddress(registration.getAddress());

        transaction.setPaymentType(RentPaymentTransaction.PaymentType.ONLINE);
        transaction.setRentAmount(rentAmount);
        transaction.setAdvanceAmount(registration.getAdvanceAmount());
        transaction.setPaidAmount(request.getAmount());
        transaction.setRemainingAmount(remainingAmount);
        transaction.setRentMonth(request.getRentMonth());
        transaction.setDueDate(request.getRentMonth().plusMonths(1).minusDays(1)); // Last day of month
        transaction.setPaidDate(LocalDate.now()); // Online payment treated as paid today
        transaction.setStatus(status);
        transaction.setPaymentMethod(request.getPaymentMethod());

        transaction.setRemarks(request.getRemarks());

        transaction.setCreatedBy(currentUsername);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());
        transaction.setUpdatedBy(currentUsername);

        RentPaymentTransaction saved = paymentRepository.save(transaction);
        log.info("Online payment recorded successfully: {}", saved.getId());

        return PaymentTransactionResponse.from(saved);
    }

    /**
     * Get tenant's payment history for last 12 months
     */
    public List<TenantPaymentHistoryItem> getTenantPaymentHistory(String tenantId) {
        log.info("Fetching payment history for tenant: {}", tenantId);

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(12);

        List<RentPaymentTransaction> transactions = paymentRepository
            .findByTenantIdAndRentMonthIsBetween(tenantId, startDate, endDate);

        return transactions.stream()
            .sorted(Comparator.comparing(RentPaymentTransaction::getRentMonth).reversed())
            .map(TenantPaymentHistoryItem::from)
            .collect(Collectors.toList());
    }

    /**
     * Get owner's payment dashboard for a specific month
     */
    public OwnerPaymentDashboard getOwnerPaymentDashboard(String coliveOwnerUsername, LocalDate month) {
        log.info("Fetching payment dashboard for owner: {} for month: {}", coliveOwnerUsername, month);

        // Set to first day of month
        LocalDate monthStart = month.withDayOfMonth(1);

        List<RentPaymentTransaction> monthlyTransactions = paymentRepository
            .findByColiveOwnerUsernameAndRentMonth(coliveOwnerUsername, monthStart);

        return buildPaymentDashboard(coliveOwnerUsername, monthStart, monthlyTransactions);
    }

    /**
     * Get owner's payment dashboard for last 6 months
     */
    public List<OwnerPaymentDashboard> getOwnerPaymentHistory(String coliveOwnerUsername) {
        log.info("Fetching 6-month payment history for owner: {}", coliveOwnerUsername);

        LocalDate endMonth = LocalDate.now();
        LocalDate startMonth = endMonth.minusMonths(5).withDayOfMonth(1);

        List<OwnerPaymentDashboard> dashboards = new ArrayList<>();
        LocalDate currentMonth = startMonth;

        while (!currentMonth.isAfter(endMonth)) {
            List<RentPaymentTransaction> transactions = paymentRepository
                .findByColiveOwnerUsernameAndRentMonth(coliveOwnerUsername, currentMonth);

            dashboards.add(buildPaymentDashboard(coliveOwnerUsername, currentMonth, transactions));
            currentMonth = currentMonth.plusMonths(1);
        }

        return dashboards;
    }

    /**
     * Build payment dashboard from transactions
     */
    private OwnerPaymentDashboard buildPaymentDashboard(String coliveOwnerUsername, LocalDate month, 
                                                         List<RentPaymentTransaction> transactions) {
        // Group by tenant
        Map<String, RentPaymentTransaction> tenantLatestPayment = transactions.stream()
            .collect(Collectors.toMap(
                RentPaymentTransaction::getTenantId,
                t -> t,
                (existing, newer) -> newer.getUpdatedAt().isAfter(existing.getUpdatedAt()) ? newer : existing
            ));

        List<OwnerTenantPaymentSummary> tenantSummaries = tenantLatestPayment.values().stream()
            .map(OwnerTenantPaymentSummary::from)
            .sorted(Comparator.comparing(OwnerTenantPaymentSummary::getStatus))
            .collect(Collectors.toList());

        // Calculate totals
        double totalExpected = tenantSummaries.stream().mapToDouble(OwnerTenantPaymentSummary::getExpectedRent).sum();
        double totalReceived = tenantSummaries.stream().mapToDouble(OwnerTenantPaymentSummary::getPaidAmount).sum();
        double totalOutstanding = tenantSummaries.stream().mapToDouble(OwnerTenantPaymentSummary::getRemainingAmount).sum();

        int paidCount = (int) tenantSummaries.stream()
            .filter(s -> s.getStatus() == RentPaymentTransaction.PaymentStatus.COMPLETED)
            .count();
        int partialCount = (int) tenantSummaries.stream()
            .filter(s -> s.getStatus() == RentPaymentTransaction.PaymentStatus.PARTIAL)
            .count();
        int unpaidCount = (int) tenantSummaries.stream()
            .filter(s -> s.getStatus() == RentPaymentTransaction.PaymentStatus.PENDING || 
                        s.getStatus() == RentPaymentTransaction.PaymentStatus.OVERDUE)
            .count();

        // Group by property
        Map<String, List<OwnerTenantPaymentSummary>> byProperty = tenantSummaries.stream()
            .collect(Collectors.groupingBy(s -> transactions.stream()
                .filter(t -> t.getTenantId().equals(s.getTenantId()))
                .findFirst()
                .map(RentPaymentTransaction::getColiveName)
                .orElse("Unknown")));

        List<OwnerPaymentDashboard.CoLivePropertyPaymentSummary> properties = byProperty.entrySet().stream()
            .map(entry -> buildPropertySummary(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());

        String monthDisplay = month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"));

        return OwnerPaymentDashboard.builder()
            .month(month)
            .monthDisplay(monthDisplay)
            .totalTenants(tenantSummaries.size())
            .totalExpectedRent(totalExpected)
            .totalReceivedRent(totalReceived)
            .totalOutstandingRent(totalOutstanding)
            .tenantsWithFullPayment(paidCount)
            .tenantsWithPartialPayment(partialCount)
            .tenantsWithNoPayment(unpaidCount)
            .properties(properties)
            .tenantPayments(tenantSummaries)
            .build();
    }

    /**
     * Build property-level payment summary
     */
    private OwnerPaymentDashboard.CoLivePropertyPaymentSummary buildPropertySummary(String coliveName, 
                                                                                     List<OwnerTenantPaymentSummary> tenantSummaries) {
        double expectedRent = tenantSummaries.stream().mapToDouble(OwnerTenantPaymentSummary::getExpectedRent).sum();
        double receivedRent = tenantSummaries.stream().mapToDouble(OwnerTenantPaymentSummary::getPaidAmount).sum();
        double outstandingRent = tenantSummaries.stream().mapToDouble(OwnerTenantPaymentSummary::getRemainingAmount).sum();

        int paidRooms = (int) tenantSummaries.stream()
            .filter(s -> s.getStatus() == RentPaymentTransaction.PaymentStatus.COMPLETED)
            .count();
        int partialRooms = (int) tenantSummaries.stream()
            .filter(s -> s.getStatus() == RentPaymentTransaction.PaymentStatus.PARTIAL)
            .count();
        int unpaidRooms = (int) tenantSummaries.stream()
            .filter(s -> s.getStatus() == RentPaymentTransaction.PaymentStatus.PENDING || 
                        s.getStatus() == RentPaymentTransaction.PaymentStatus.OVERDUE)
            .count();

        return OwnerPaymentDashboard.CoLivePropertyPaymentSummary.builder()
            .coliveName(coliveName)
            .totalRooms(tenantSummaries.size())
            .occupiedRooms(tenantSummaries.size())
            .expectedRent(expectedRent)
            .receivedRent(receivedRent)
            .outstandingRent(outstandingRent)
            .paidRooms(paidRooms)
            .partialRooms(partialRooms)
            .unpaidRooms(unpaidRooms)
            .build();
    }

    /**
     * Update Razorpay payment details after successful verification
     */
    public PaymentTransactionResponse updateRazorpayPaymentDetails(String transactionId, String paymentId, String signature) {
        log.info("Updating Razorpay payment details for transaction: {}", transactionId);

        Optional<RentPaymentTransaction> transaction = paymentRepository.findById(transactionId);
        if (transaction.isEmpty()) {
            throw new IllegalArgumentException("Transaction not found with ID: " + transactionId);
        }

        RentPaymentTransaction payment = transaction.get();
        payment.setRazorpayPaymentId(paymentId);
        payment.setRazorpaySignature(signature);
        payment.setStatus(RentPaymentTransaction.PaymentStatus.COMPLETED);
        payment.setUpdatedAt(LocalDateTime.now());

        RentPaymentTransaction saved = paymentRepository.save(payment);
        return PaymentTransactionResponse.from(saved);
    }

    /**
     * (Requirement #3) Record a prorated/partial month cash payment
     * For tenants who join mid-month, calculate rent for remaining days only
     */
    public PaymentTransactionResponse recordProratedCashPayment(ProratedCashPaymentRequest request, String currentUsername) {
        log.info("Recording prorated payment for tenant: {} from {} to {}", 
            request.getTenantId(), request.getProratedStartDate(), request.getProratedEndDate());

        Optional<RegistrationDocument> tenant = registrationRepository.findById(request.getTenantId());
        if (tenant.isEmpty()) {
            throw new IllegalArgumentException("Tenant not found with ID: " + request.getTenantId());
        }

        RegistrationDocument registration = tenant.get();

        RentPaymentTransaction transaction = new RentPaymentTransaction();

        // Set prorated payment details
        transaction.setIsProratedPayment(true);
        transaction.setProratedStartDate(request.getProratedStartDate());
        transaction.setProratedEndDate(request.getProratedEndDate());
        transaction.setProratedDaysCount(request.getNumberOfDays());
        transaction.setProratedAmount(request.getProratedAmount());

        // Set next billing cycle start (1st of next month after prorated period ends)
        LocalDate nextCycleStart = request.getProratedEndDate().plusDays(1).withDayOfMonth(1);
        transaction.setNextBillingCycleStart(nextCycleStart);

        // Set basic transaction details
        transaction.setTenantId(registration.getId());
        transaction.setTenantName(registration.getFname() + " " + registration.getLname());
        transaction.setTenantEmail(registration.getEmail());
        transaction.setTenantPhone(registration.getContactNo());
        transaction.setColiveName(registration.getColiveName());
        transaction.setColiveOwnerUsername(registration.getColiveUserName());
        transaction.setRoomNumber(registration.getRoomForRegistration() != null ? 
            registration.getRoomForRegistration().getRoomNumber() : "N/A");
        transaction.setPropertyAddress(registration.getAddress());

        transaction.setPaymentType(RentPaymentTransaction.PaymentType.CASH);
        transaction.setRentAmount(request.getProratedAmount());
        transaction.setPaidAmount(request.getProratedAmount());
        transaction.setRemainingAmount(0);
        transaction.setStatus(RentPaymentTransaction.PaymentStatus.PENDING_APPROVAL); // Awaits moderator approval
        transaction.setApprovalStatus(RentPaymentTransaction.ApprovalStatus.PENDING_APPROVAL);
        
        transaction.setRentMonth(request.getProratedStartDate().withDayOfMonth(1));
        transaction.setDueDate(request.getProratedEndDate());
        transaction.setPaidDate(request.getCashReceivedDate());
        transaction.setPaymentMethod("CASH");

        transaction.setReceiptNumber(request.getReceiptNumber());
        transaction.setCashReceivedDate(request.getCashReceivedDate());
        transaction.setRemarks(request.getRemarks());

        transaction.setCreatedBy(currentUsername);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());

        RentPaymentTransaction saved = paymentRepository.save(transaction);
        log.info("Prorated payment recorded: {}", saved.getId());

        return PaymentTransactionResponse.from(saved);
    }

    /**
     * (Requirement #2) Get pending cash payment approvals for a moderator
     */
    public List<PendingPaymentApprovalItem> getPendingPaymentApprovals(String coliveOwnerUsername) {
        log.info("Fetching pending payment approvals for owner: {}", coliveOwnerUsername);

        List<RentPaymentTransaction> pendingApprovals = paymentRepository
            .findByColiveOwnerUsernameAndApprovalStatus(coliveOwnerUsername, 
                RentPaymentTransaction.ApprovalStatus.PENDING_APPROVAL);

        return pendingApprovals.stream()
            .sorted(Comparator.comparing(RentPaymentTransaction::getCreatedAt).reversed())
            .map(PendingPaymentApprovalItem::from)
            .collect(Collectors.toList());
    }

    /**
     * (Requirement #2) Approve or reject a cash payment by moderator
     */
    public PaymentTransactionResponse approveRejectPayment(PaymentApprovalRequest request, String moderatorUsername) {
        log.info("Processing approval for transaction: {} - Approve: {}", request.getTransactionId(), request.getApprove());

        Optional<RentPaymentTransaction> transactionOpt = paymentRepository.findById(request.getTransactionId());
        if (transactionOpt.isEmpty()) {
            throw new IllegalArgumentException("Transaction not found with ID: " + request.getTransactionId());
        }

        RentPaymentTransaction transaction = transactionOpt.get();

        if (request.getApprove()) {
            // APPROVE
            transaction.setApprovalStatus(RentPaymentTransaction.ApprovalStatus.APPROVED);
            transaction.setStatus(RentPaymentTransaction.PaymentStatus.COMPLETED);
            transaction.setApprovalRemarks(request.getRemarks());
            log.info("Payment approved: {}", transaction.getId());
        } else {
            // REJECT
            transaction.setApprovalStatus(RentPaymentTransaction.ApprovalStatus.REJECTED);
            transaction.setStatus(RentPaymentTransaction.PaymentStatus.CANCELLED);
            transaction.setRejectionReason(request.getRemarks());
            log.info("Payment rejected: {}", transaction.getId());
        }

        transaction.setApprovedBy(moderatorUsername);
        transaction.setApprovedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());
        transaction.setUpdatedBy(moderatorUsername);

        RentPaymentTransaction saved = paymentRepository.save(transaction);
        return PaymentTransactionResponse.from(saved);
    }

    /**
     * (Requirement #4) Get tenant's total outstanding balance across all months and properties
     */
    public TenantOutstandingBalance getTenantOutstandingBalance(String tenantId) {
        log.info("Calculating outstanding balance for tenant: {}", tenantId);

        Optional<RegistrationDocument> tenant = registrationRepository.findById(tenantId);
        if (tenant.isEmpty()) {
            throw new IllegalArgumentException("Tenant not found with ID: " + tenantId);
        }

        RegistrationDocument registration = tenant.get();

        // Get all payments for this tenant
        List<RentPaymentTransaction> allPayments = paymentRepository.findByTenantId(tenantId);

        // Filter unpaid/partial payments
        List<RentPaymentTransaction.PaymentStatus> unpaidStatuses = Arrays.asList(
            RentPaymentTransaction.PaymentStatus.PENDING,
            RentPaymentTransaction.PaymentStatus.PARTIAL,
            RentPaymentTransaction.PaymentStatus.OVERDUE
        );
        
        List<RentPaymentTransaction> outstandingPayments = allPayments.stream()
            .filter(p -> unpaidStatuses.contains(p.getStatus()))
            .collect(Collectors.toList());

        // Group by month and coLive
        Map<String, TenantOutstandingBalance.OutstandingMonth> monthlyBreakdown = new HashMap<>();
        double totalOutstanding = 0;
        double totalAdvanceUsed = 0;

        for (RentPaymentTransaction payment : outstandingPayments) {
            long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(payment.getDueDate(), LocalDate.now());
            
            String key = payment.getRentMonth() + "-" + payment.getColiveName();
            
            TenantOutstandingBalance.OutstandingMonth month = TenantOutstandingBalance.OutstandingMonth.builder()
                .rentMonth(payment.getRentMonth().toString())
                .coliveName(payment.getColiveName())
                .roomNumber(payment.getRoomNumber())
                .expectedAmount(payment.getRentAmount())
                .paidAmount(payment.getPaidAmount())
                .outstandingAmount(payment.getRemainingAmount())
                .status(payment.getStatus().toString())
                .daysOverdue(daysOverdue > 0 ? daysOverdue : 0)
                .build();
            
            monthlyBreakdown.put(key, month);
            totalOutstanding += payment.getRemainingAmount();
        }

        if (registration.getAdvanceAmount() > 0) {
            totalAdvanceUsed = registration.getAdvanceAmount();
        }

        return TenantOutstandingBalance.builder()
            .tenantId(tenantId)
            .tenantName(registration.getFname() + " " + registration.getLname())
            .totalOutstanding(totalOutstanding)
            .totalAdvanceUsed(totalAdvanceUsed)
            .monthlyBreakdown(new ArrayList<>(monthlyBreakdown.values()))
            .build();
    }

    /**
     * (Requirement #5) Get moderator's collection details/report up to current date and time
     */
    public ModeratorCollectionReport getModeratorCollectionReport(String coliveOwnerUsername, LocalDateTime upToDateTime) {
        log.info("Generating collection report for owner: {} up to: {}", coliveOwnerUsername, upToDateTime);

        LocalDate today = upToDateTime.toLocalDate();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);

        // Get all transactions for this coLive up to the given datetime
        List<RentPaymentTransaction> allTransactions = paymentRepository
            .findByColiveOwnerUsernameAndCollectionDateTimeIsBefore(coliveOwnerUsername, upToDateTime);

        // Separate by status
        List<RentPaymentTransaction> approvedPayments = allTransactions.stream()
            .filter(t -> t.getApprovalStatus() == RentPaymentTransaction.ApprovalStatus.APPROVED ||
                        t.getPaymentType() == RentPaymentTransaction.PaymentType.ONLINE)
            .collect(Collectors.toList());

        List<RentPaymentTransaction> pendingApprovals = allTransactions.stream()
            .filter(t -> t.getApprovalStatus() == RentPaymentTransaction.ApprovalStatus.PENDING_APPROVAL)
            .collect(Collectors.toList());

        List<RentPaymentTransaction> rejectedPayments = allTransactions.stream()
            .filter(t -> t.getApprovalStatus() == RentPaymentTransaction.ApprovalStatus.REJECTED)
            .collect(Collectors.toList());

        // Calculate totals
        double totalCash = approvedPayments.stream()
            .filter(t -> t.getPaymentType() == RentPaymentTransaction.PaymentType.CASH)
            .mapToDouble(RentPaymentTransaction::getPaidAmount)
            .sum();

        double totalOnline = approvedPayments.stream()
            .filter(t -> t.getPaymentType() == RentPaymentTransaction.PaymentType.ONLINE)
            .mapToDouble(RentPaymentTransaction::getPaidAmount)
            .sum();

        double totalCollected = totalCash + totalOnline;

        // Build transaction details
        List<ModeratorCollectionReport.CollectionTransactionDetail> transactionDetails = allTransactions.stream()
            .map(t -> ModeratorCollectionReport.CollectionTransactionDetail.builder()
                .transactionId(t.getId())
                .tenantName(t.getTenantName())
                .roomNumber(t.getRoomNumber())
                .amount(t.getPaidAmount())
                .paymentType(t.getPaymentType().toString())
                .paymentMethod(t.getPaymentMethod())
                .rentMonth(t.getRentMonth().toString())
                .collectionTime(t.getCollectionDateTime() != null ? t.getCollectionDateTime() : t.getCreatedAt())
                .status(t.getStatus().toString())
                .receiptNumber(t.getReceiptNumber())
                .isProratedPayment(t.getIsProratedPayment())
                .proratedDaysCount(t.getProratedDaysCount())
                .approvalRemarks(t.getApprovalRemarks())
                .approvedAt(t.getApprovedAt())
                .build())
            .sorted(Comparator.comparing(ModeratorCollectionReport.CollectionTransactionDetail::getCollectionTime).reversed())
            .collect(Collectors.toList());

        String period = "Up to " + upToDateTime.format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm"));

        return ModeratorCollectionReport.builder()
            .reportGeneratedAt(LocalDateTime.now())
            .moderatorUsername(coliveOwnerUsername)
            .period(period)
            .totalTransactionsProcessed(approvedPayments.size())
            .totalCashCollected(totalCash)
            .totalOnlineCollected(totalOnline)
            .totalCollected(totalCollected)
            .totalApprovedPayments(approvedPayments.size())
            .totalPendingApprovals(pendingApprovals.size())
            .totalRejectedPayments(rejectedPayments.size())
            .transactions(transactionDetails)
            .build();
    }
}
