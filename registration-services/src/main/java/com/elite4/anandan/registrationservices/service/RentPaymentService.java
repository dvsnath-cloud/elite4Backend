package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.document.RegistrationDocument;
import com.elite4.anandan.registrationservices.document.RentPaymentTransaction;
import com.elite4.anandan.registrationservices.dto.*;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.RentPaymentTransactionRepository;
import com.elite4.anandan.registrationservices.repository.RegistrationRepository;
import com.elite4.anandan.registrationservices.repository.UserRepository;
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
    private final UserRepository userRepository;
    
    /**
     * Fetch bank details for a property using coliveUserName and coliveName
     * This is called by the tenant to know where to transfer rent
     */
    public Map<String, Object> getBankDetailsByColiveAndRoomOwner(String coliveUserName, String coliveName) {
        log.info("Fetching bank details for coliveUserName: {} and coliveName: {}", coliveUserName, coliveName);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Find the User (property owner) by username
            var userOptional = userRepository.findByUsername(coliveUserName);
            
            if (userOptional.isEmpty()) {
                log.warn("User not found for coliveUserName: {}", coliveUserName);
                response.put("success", true);
                response.put("message", "Property owner not found");
                response.put("bankDetails", null);
                return response;
            }
            
            User user = userOptional.get();
            
            // Find the specific property (clientDetail) by coliveName
            if (user.getClientDetails() == null || user.getClientDetails().isEmpty()) {
                log.warn("No properties found for user: {}", coliveUserName);
                response.put("success", true);
                response.put("message", "No properties found for this owner");
                response.put("bankDetails", null);
                return response;
            }
            
            // Try exact match first
            ClientAndRoomOnBoardId clientDetail = user.getClientDetails().stream()
                    .filter(c -> coliveName.equals(c.getColiveName()))
                    .findFirst()
                    .orElse(null);
            
            // Try case-insensitive match if exact match fails
            if (clientDetail == null) {
                clientDetail = user.getClientDetails().stream()
                        .filter(c -> c.getColiveName() != null && 
                                   c.getColiveName().equalsIgnoreCase(coliveName))
                        .findFirst()
                        .orElse(null);
            }
            
            // If still not found, log available properties and use first with bank details
            if (clientDetail == null) {
                log.warn("No exact match for coliveName: {}. Available properties: {}", coliveName,
                        user.getClientDetails().stream()
                                .map(ClientAndRoomOnBoardId::getColiveName)
                                .toList());
                
                clientDetail = user.getClientDetails().stream()
                        .filter(c -> c.getBankDetails() != null)
                        .findFirst()
                        .orElse(null);
            }
            
            if (clientDetail != null && clientDetail.getBankDetails() != null) {
                response.put("success", true);
                response.put("message", "Bank details retrieved successfully");
                response.put("bankDetails", clientDetail.getBankDetails());
                response.put("coliveName", clientDetail.getColiveName());
                response.put("coliveOwnerUsername", coliveUserName);
                log.info("✅ Bank details found for property {}: {}", coliveName, clientDetail.getBankDetails().getBankName());
            } else {
                log.warn("No bank details found for property: {}", coliveName);
                response.put("success", true);
                response.put("message", "Bank details not configured for this property");
                response.put("bankDetails", null);
                response.put("coliveName", coliveName);
                response.put("coliveOwnerUsername", coliveUserName);
            }
            
        } catch (Exception e) {
            log.error("Error fetching bank details for coliveUserName: {} and coliveName: {}", coliveUserName, coliveName, e);
            response.put("success", false);
            response.put("message", "Error fetching bank details: " + e.getMessage());
        }
        
        return response;
    }

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

        // Calculate payment status based on rent amount only
        double rentAmount = registration.getRoomRent();
        double advanceIncluded = request.getAdvanceAmount() != null ? request.getAdvanceAmount() : 0;
        double rentPaid = request.getAmount() - advanceIncluded;
        
        double remainingAmount = Math.max(0, rentAmount - rentPaid);
        RentPaymentTransaction.PaymentStatus status = rentPaid >= rentAmount ? 
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
        transaction.setAdvanceAmount(advanceIncluded);  // Track actual advance paid in this transaction
        transaction.setPaidAmount(request.getAmount());  // Total: rent + advance
        transaction.setRemainingAmount(remainingAmount);  // Remaining rent only
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
        transaction.setCollectionDateTime(LocalDateTime.now());

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

        // Calculate payment status based on rent amount only
        double rentAmount = registration.getRoomRent();
        double advanceIncluded = request.getAdvanceAmount() != null ? request.getAdvanceAmount() : 0;
        double rentPaid = request.getAmount() - advanceIncluded;
        
        double remainingAmount = Math.max(0, rentAmount - rentPaid);
        RentPaymentTransaction.PaymentStatus status = rentPaid >= rentAmount ? 
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
        transaction.setAdvanceAmount(advanceIncluded);  // Track actual advance paid
        transaction.setPaidAmount(request.getAmount());  // Total: rent + advance
        transaction.setRemainingAmount(remainingAmount);  // Remaining rent only
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
        transaction.setCollectionDateTime(LocalDateTime.now());

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

        // Extract advance amount if included in payment
        double advanceIncluded = request.getAdvanceAmount() != null ? request.getAdvanceAmount() : 0;

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
        transaction.setAdvanceAmount(advanceIncluded);  // Track actual advance paid
        transaction.setPaidAmount(request.getProratedAmount() + advanceIncluded);  // Total: prorated + advance
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
        transaction.setCollectionDateTime(LocalDateTime.now());

        RentPaymentTransaction saved = paymentRepository.save(transaction);
        log.info("Prorated payment recorded: {}", saved.getId());

        return PaymentTransactionResponse.from(saved);
    }

    /**
     * (Requirement #2) Get pending cash payment approvals for a moderator
     */
    public List<PendingPaymentApprovalItem> getPendingPaymentApprovals(String coliveOwnerUsername, String coliveName) {
        log.info("Fetching pending payment approvals for owner: {}, coliveName: {}", coliveOwnerUsername, coliveName);

        List<RentPaymentTransaction> pendingApprovals;
        if (coliveName != null && !coliveName.isBlank()) {
            pendingApprovals = paymentRepository
                .findByColiveOwnerUsernameAndColiveNameAndApprovalStatus(coliveOwnerUsername, coliveName,
                    RentPaymentTransaction.ApprovalStatus.PENDING_APPROVAL);
        } else {
            pendingApprovals = paymentRepository
                .findByColiveOwnerUsernameAndApprovalStatus(coliveOwnerUsername, 
                    RentPaymentTransaction.ApprovalStatus.PENDING_APPROVAL);
        }

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
     * Get moderator's collection report showing payment status of each room for the requested month
     * Returns ROOM-BASED view, not transaction-based
     */
    public ModeratorCollectionReport getMonthlyCollectionStatus(String coliveOwnerUsername, String monthStr, String coliveName) {
        log.info("Generating monthly collection status for owner: {} month: {} coliveName: {}", coliveOwnerUsername, monthStr, coliveName);

        // Parse month string (YYYY-MM)
        String[] parts = monthStr.split("-");
        int year = Integer.parseInt(parts[0]);
        int monthNum = Integer.parseInt(parts[1]);
        LocalDate rentMonth = LocalDate.of(year, monthNum, 1);

        // Get all registrations for this moderator (optionally filtered by coliveName)
        List<RegistrationDocument> registrations;
        if (coliveName != null && !coliveName.isBlank()) {
            registrations = registrationRepository.findByColiveNameAndColiveUserName(coliveName, coliveOwnerUsername);
        } else {
            registrations = registrationRepository.findByOccupiedAndColiveUserName(Registration.roomOccupied.OCCUPIED, coliveOwnerUsername);
        }

        log.info("Found {} registrations for moderator: {} in month: {}", registrations.size(), coliveOwnerUsername, monthStr);

        // Get all payments for this moderator (we'll filter by month in code to handle late payments)
        List<RentPaymentTransaction> allPayments = paymentRepository.findByColiveOwnerUsername(coliveOwnerUsername);
        
        // Filter payments by month - include if EITHER rentMonth OR paidDate month matches
        // This captures both regular payments and late/advance payments made in the current month
        List<RentPaymentTransaction> monthlyPayments = allPayments.stream()
            .filter(p -> {
                boolean rentMonthMatches = p.getRentMonth() != null && 
                    p.getRentMonth().getYear() == year && 
                    p.getRentMonth().getMonthValue() == monthNum;
                
                boolean paidDateMatches = p.getPaidDate() != null && 
                    p.getPaidDate().getYear() == year && 
                    p.getPaidDate().getMonthValue() == monthNum;
                
                boolean matchesMonth = rentMonthMatches || paidDateMatches;
                if (matchesMonth) {
                    log.debug("Payment matches month {}-{}: tenantId={}, rentMonth={}, paidDate={}", 
                        year, monthNum, p.getTenantId(), p.getRentMonth(), p.getPaidDate());
                }
                return matchesMonth;
            })
            .collect(Collectors.toList());
        
        // If coliveName specified, further filter payments
        if (coliveName != null && !coliveName.isBlank()) {
            monthlyPayments = monthlyPayments.stream()
                .filter(p -> coliveName.equals(p.getColiveName()))
                .collect(Collectors.toList());
        }

        log.info("Found {} payments for moderator: {} in month: {} (including late/advance payments)", monthlyPayments.size(), coliveOwnerUsername, monthStr);

        // Build a map of registration ID (tenantId) to payment for quick lookup
        Map<String, RentPaymentTransaction> paymentsByRegistrationId = monthlyPayments.stream()
            .peek(p -> log.debug("Payment details - tenantId: {}, amount: {}, type: {}, method: {}, status: {}", 
                p.getTenantId(), p.getPaidAmount(), p.getPaymentType(), p.getPaymentMethod(), p.getStatus()))
            .collect(Collectors.toMap(RentPaymentTransaction::getTenantId, p -> p, (p1, p2) -> p1));

        log.info("Payment map contains {} entries", paymentsByRegistrationId.size());

        // Convert registrations to RoomPaymentStatus
        List<ModeratorCollectionReport.RoomPaymentStatus> rooms = registrations.stream()
            .map(reg -> {
                try {
                    log.debug("Processing registration: id={}, room={}, tenant={}", reg.getId(), 
                        reg.getRoomForRegistration() != null ? reg.getRoomForRegistration().getRoomNumber() : "N/A", 
                        reg.getFname());
                    
                    RentPaymentTransaction payment = paymentsByRegistrationId.get(reg.getId());
                    if (payment == null) {
                        log.debug("No payment found for registration {} in payment map", reg.getId());
                    } else {
                        log.debug("Found payment for registration {}: amount={}", reg.getId(), payment.getPaidAmount());
                    }
                    
                    String status = "UNPAID";
                    Double paidAmount = 0.0;
                    LocalDate paidDate = null;
                    String paymentMethod = null;
                    String paymentType = null;
                    String receiptNumber = null;
                    Boolean isProratedPayment = false;
                    Integer proratedDaysCount = null;
                    LocalDateTime transactionTime = null;

                    if (payment != null && (payment.getStatus() == RentPaymentTransaction.PaymentStatus.COMPLETED ||
                                           payment.getStatus() == RentPaymentTransaction.PaymentStatus.PENDING_APPROVAL)) {
                        paidAmount = payment.getPaidAmount();
                        log.debug("Found payment for registration {}: amount={}, type={}, method={}, status={}", 
                            reg.getId(), paidAmount, payment.getPaymentType(), payment.getPaymentMethod(), payment.getStatus());
                        
                        Double rentVal = reg.getRoomRent();
                        double rentAmount = (rentVal != null) ? rentVal.doubleValue() : 0.0;
                        
                        // Fixed status calculation: if paid >= rent, it's PAID (including advance/prorated)
                        if (paidAmount >= rentAmount - 0.01) {
                            status = "PAID";
                        } else if (paidAmount > 0) {
                            status = "PARTIAL";
                        } else {
                            status = "UNPAID";
                        }
                        
                        paidDate = payment.getRentMonth();
                        paymentMethod = payment.getPaymentMethod();
                        paymentType = payment.getPaymentType() != null ? payment.getPaymentType().toString() : null;
                        receiptNumber = payment.getReceiptNumber();
                        isProratedPayment = payment.getIsProratedPayment();
                        proratedDaysCount = payment.getProratedDaysCount();
                        transactionTime = payment.getCollectionDateTime() != null ? payment.getCollectionDateTime() : payment.getCreatedAt();
                    }

                    Double rentVal = reg.getRoomRent();
                    String roomNumber = "N/A";
                    try {
                        if (reg.getRoomForRegistration() != null && reg.getRoomForRegistration().getRoomNumber() != null) {
                            roomNumber = reg.getRoomForRegistration().getRoomNumber();
                        }
                    } catch (Exception e) {
                        log.warn("Error getting room number for registration: {}", reg.getId(), e);
                    }
                    
                    String tenantName = "Unknown";
                    try {
                        String fname = reg.getFname() != null ? reg.getFname() : "";
                        String lname = reg.getLname() != null ? reg.getLname() : "";
                        tenantName = (fname + " " + lname).trim();
                        if (tenantName.isEmpty()) tenantName = "Unknown";
                    } catch (Exception e) {
                        log.warn("Error getting tenant name for registration: {}", reg.getId(), e);
                    }

                    return ModeratorCollectionReport.RoomPaymentStatus.builder()
                        .registrationId(reg.getId())
                        .roomNumber(roomNumber)
                        .tenantName(tenantName)
                        .coliveUserName(reg.getColiveUserName() != null ? reg.getColiveUserName() : "")
                        .rentAmount((rentVal != null) ? rentVal.doubleValue() : 0.0)
                        .advanceAmount(reg.getAdvanceAmount())
                        .paidAmount(paidAmount)
                        .status(status)
                        .paidDate(paidDate)
                        .paymentMethod(paymentMethod)
                        .paymentType(paymentType)
                        .receiptNumber(receiptNumber)
                        .isProratedPayment(isProratedPayment)
                        .proratedDaysCount(proratedDaysCount)
                        .transactionTime(transactionTime)
                        .build();
                } catch (Exception e) {
                    log.error("Error processing registration: {}", reg.getId(), e);
                    // Return a safe default entry
                    return ModeratorCollectionReport.RoomPaymentStatus.builder()
                        .registrationId(reg.getId())
                        .roomNumber("ERROR")
                        .tenantName("Error Processing")
                        .coliveUserName("")
                        .rentAmount(0.0)
                        .paidAmount(0.0)
                        .status("UNPAID")
                        .build();
                }
            })
            .sorted(Comparator.nullsLast(Comparator.comparing(ModeratorCollectionReport.RoomPaymentStatus::getRoomNumber)))
            .collect(Collectors.toList());

        // Calculate summary statistics
        int totalRooms = rooms.size();
        int totalPaidRooms = (int) rooms.stream().filter(r -> "PAID".equals(r.getStatus())).count();
        int totalUnpaidRooms = (int) rooms.stream().filter(r -> "UNPAID".equals(r.getStatus())).count();
        int totalPartialRooms = (int) rooms.stream().filter(r -> "PARTIAL".equals(r.getStatus())).count();

        double totalExpectedRent = rooms.stream().mapToDouble(ModeratorCollectionReport.RoomPaymentStatus::getRentAmount).sum();
        double totalCollected = rooms.stream().mapToDouble(ModeratorCollectionReport.RoomPaymentStatus::getPaidAmount).sum();
        double totalPending = totalExpectedRent - totalCollected;

        String period = monthStr;

        return ModeratorCollectionReport.builder()
            .reportGeneratedAt(LocalDateTime.now())
            .moderatorUsername(coliveOwnerUsername)
            .coliveName(coliveName)
            .period(period)
            .totalRooms(totalRooms)
            .totalPaidRooms(totalPaidRooms)
            .totalUnpaidRooms(totalUnpaidRooms)
            .totalPartialRooms(totalPartialRooms)
            .totalExpectedRent(totalExpectedRent)
            .totalCollected(totalCollected)
            .totalPending(totalPending)
            .rooms(rooms)
            .build();
    }

    /**
     * Consolidated payment summary for a tenant — single API call replaces
     * multiple frontend calls and all client-side computation.
     */
    public TenantPaymentSummary getTenantPaymentSummary(String tenantId) {
        log.info("Building consolidated payment summary for tenant: {}", tenantId);

        // 1. Fetch tenant registration
        RegistrationDocument registration = registrationRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found with ID: " + tenantId));

        double roomRent = registration.getRoomRent();
        double advanceAmount = registration.getAdvanceAmount();
        LocalDate checkInDate = registration.getCheckInDate() != null
                ? registration.getCheckInDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                : null;
        String roomNumber = registration.getRoomForRegistration() != null
                ? registration.getRoomForRegistration().getRoomNumber() : "N/A";

        // 2. Fetch all payments for this tenant (single DB call)
        List<RentPaymentTransaction> allPayments = paymentRepository.findByTenantId(tenantId);

        // 3. Current month payment check
        LocalDate currentMonthStart = LocalDate.now().withDayOfMonth(1);
        TenantPaymentHistoryItem currentMonthPayment = allPayments.stream()
                .filter(p -> p.getRentMonth() != null && p.getRentMonth().equals(currentMonthStart))
                .filter(p -> p.getStatus() == RentPaymentTransaction.PaymentStatus.COMPLETED
                        || p.getStatus() == RentPaymentTransaction.PaymentStatus.PENDING_APPROVAL)
                .max(Comparator.comparing(RentPaymentTransaction::getUpdatedAt))
                .map(TenantPaymentHistoryItem::from)
                .orElse(null);

        boolean currentMonthPaid = currentMonthPayment != null;

        // 4. Payment history — last 6 months, sorted newest first
        LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6);
        List<TenantPaymentHistoryItem> paymentHistory = allPayments.stream()
                .filter(p -> p.getRentMonth() != null && !p.getRentMonth().isBefore(sixMonthsAgo))
                .sorted(Comparator.comparing(RentPaymentTransaction::getRentMonth).reversed())
                .limit(6)
                .map(TenantPaymentHistoryItem::from)
                .collect(Collectors.toList());

        // 5. First-time payment detection
        boolean isFirstTime = allPayments.isEmpty() && currentMonthPayment == null;

        // 6. Prorated info (only for first-time tenants joined after 1st)
        TenantPaymentSummary.ProratedInfo proratedInfo = null;
        if (isFirstTime && checkInDate != null && checkInDate.getDayOfMonth() > 1) {
            LocalDate now = LocalDate.now();
            int joinDay = checkInDate.getDayOfMonth();

            // Use join month if same as current, otherwise use current month
            int year = (checkInDate.getMonth() == now.getMonth() && checkInDate.getYear() == now.getYear())
                    ? checkInDate.getYear() : now.getYear();
            int monthValue = (checkInDate.getMonth() == now.getMonth() && checkInDate.getYear() == now.getYear())
                    ? checkInDate.getMonthValue() : now.getMonthValue();

            YearMonth ym = YearMonth.of(year, monthValue);
            int daysInMonth = ym.lengthOfMonth();
            int remainingDays = daysInMonth - joinDay + 1;
            double proratedAmount = Math.round((roomRent / daysInMonth) * remainingDays);

            String ordJoin = getOrdinal(joinDay);
            String monthName = ym.getMonth().toString().substring(0, 3);
            String ordEnd = getOrdinal(daysInMonth);

            String cycleLabel1st = joinDay + ordJoin + " " + monthName + " → " + daysInMonth + ordEnd + " " + monthName + " (then 1st to 1st)";
            String cycleLabelJoin = joinDay + ordJoin + " of every month (" + joinDay + ordJoin + " → " + joinDay + ordJoin + ")";

            proratedInfo = TenantPaymentSummary.ProratedInfo.builder()
                    .startDate(LocalDate.of(year, monthValue, joinDay))
                    .endDate(LocalDate.of(year, monthValue, daysInMonth))
                    .totalDaysInMonth(daysInMonth)
                    .remainingDays(remainingDays)
                    .proratedAmount(proratedAmount)
                    .fullRent(roomRent)
                    .cycleLabel1st(cycleLabel1st)
                    .cycleLabelJoin(cycleLabelJoin)
                    .build();
        }

        // 7. Outstanding balance
        List<RentPaymentTransaction.PaymentStatus> unpaidStatuses = Arrays.asList(
                RentPaymentTransaction.PaymentStatus.PENDING,
                RentPaymentTransaction.PaymentStatus.PARTIAL,
                RentPaymentTransaction.PaymentStatus.OVERDUE);

        List<TenantOutstandingBalance.OutstandingMonth> outstandingMonths = allPayments.stream()
                .filter(p -> unpaidStatuses.contains(p.getStatus()))
                .map(p -> {
                    long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(p.getDueDate(), LocalDate.now());
                    return TenantOutstandingBalance.OutstandingMonth.builder()
                            .rentMonth(p.getRentMonth() != null ? p.getRentMonth().toString() : "")
                            .coliveName(p.getColiveName())
                            .roomNumber(p.getRoomNumber())
                            .expectedAmount(p.getRentAmount())
                            .paidAmount(p.getPaidAmount())
                            .outstandingAmount(p.getRemainingAmount())
                            .status(p.getStatus().toString())
                            .daysOverdue(Math.max(daysOverdue, 0))
                            .build();
                })
                .collect(Collectors.toList());

        double totalOutstanding = outstandingMonths.stream()
                .mapToDouble(TenantOutstandingBalance.OutstandingMonth::getOutstandingAmount)
                .sum();

        double totalAdvanceUsed = advanceAmount > 0 ? advanceAmount : 0;

        // 8. Build consolidated response
        return TenantPaymentSummary.builder()
                .tenantId(tenantId)
                .tenantName(registration.getFname() + " " + registration.getLname())
                .coliveName(registration.getColiveName())
                .roomNumber(roomNumber)
                .roomRent(roomRent)
                .advanceAmount(advanceAmount)
                .checkInDate(checkInDate)
                .currentMonthPaid(currentMonthPaid)
                .currentMonthPayment(currentMonthPayment)
                .firstTimePayment(isFirstTime)
                .proratedInfo(proratedInfo)
                .totalOutstanding(totalOutstanding)
                .totalAdvanceUsed(totalAdvanceUsed)
                .outstandingMonths(outstandingMonths)
                .paymentHistory(paymentHistory)
                .build();
    }

    private String getOrdinal(int n) {
        String[] suffixes = {"th", "st", "nd", "rd"};
        int v = n % 100;
        String suffix = (v >= 11 && v <= 13) ? "th" : suffixes[Math.min(v % 10, 3)];
        return suffix;
    }
}
