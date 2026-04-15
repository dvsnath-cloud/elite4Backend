package com.elite4.anandan.registrationservices.repository;

import com.elite4.anandan.registrationservices.document.RentPaymentTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RentPaymentTransactionRepository extends MongoRepository<RentPaymentTransaction, String> {

    /**
     * Find all payments for a specific tenant
     */
    List<RentPaymentTransaction> findByTenantId(String tenantId);

    /**
     * Find payments for a tenant in a specific month
     */
    Optional<RentPaymentTransaction> findByTenantIdAndRentMonth(String tenantId, LocalDate rentMonth);

    /**
     * Find last 12 months of payments for a tenant
     */
    List<RentPaymentTransaction> findByTenantIdAndRentMonthIsBetween(String tenantId, LocalDate startDate, LocalDate endDate);

    /**
     * Find all payments for a coLive owner
     */
    List<RentPaymentTransaction> findByColiveOwnerUsername(String coliveOwnerUsername);

    /**
     * Find payments for a coLive property in a specific month
     */
    List<RentPaymentTransaction> findByColiveNameAndRentMonth(String coliveName, LocalDate rentMonth);

    /**
     * Find payments for a coLive property within a date range
     */
    List<RentPaymentTransaction> findByColiveNameAndRentMonthIsBetween(String coliveName, LocalDate startDate, LocalDate endDate);

    /**
     * Find payments for a coLive owner for all properties in a specific month
     */
    List<RentPaymentTransaction> findByColiveOwnerUsernameAndRentMonth(String coliveOwnerUsername, LocalDate rentMonth);

    /**
     * Find payments for a coLive owner within a date range
     */
    List<RentPaymentTransaction> findByColiveOwnerUsernameAndRentMonthIsBetween(String coliveOwnerUsername, LocalDate startDate, LocalDate endDate);

    /**
     * Find payments by status
     */
    List<RentPaymentTransaction> findByStatus(RentPaymentTransaction.PaymentStatus status);

    /**
     * Find overdue payments
     */
    @Query("{ 'status': { $in: ['PENDING', 'PARTIAL', 'OVERDUE'] }, 'dueDate': { $lt: ?0 } }")
    List<RentPaymentTransaction> findOverduePayments(LocalDate today);

    /**
     * Find payments by Razorpay order ID
     */
    Optional<RentPaymentTransaction> findByRazorpayOrderId(String orderId);

    /**
     * Find payments by Razorpay payment ID
     */
    Optional<RentPaymentTransaction> findByRazorpayPaymentId(String paymentId);

    /**
     * Find payments for a room in a specific month
     */
    Optional<RentPaymentTransaction> findByRoomIdAndRentMonth(String roomId, LocalDate rentMonth);

    /**
     * Count payments by status for a coLive owner in a month
     */
    long countByColiveOwnerUsernameAndRentMonthAndStatus(String coliveOwnerUsername, LocalDate rentMonth, RentPaymentTransaction.PaymentStatus status);

    /**
     * Count total tenants who paid in a month
     */
    long countByColiveOwnerUsernameAndRentMonthAndStatusIn(String coliveOwnerUsername, LocalDate rentMonth, List<RentPaymentTransaction.PaymentStatus> statuses);

    /**
     * Find pending approvals for a coLive (all cash payments awaiting approval)
     */
    List<RentPaymentTransaction> findByColiveOwnerUsernameAndApprovalStatus(String coliveOwnerUsername, RentPaymentTransaction.ApprovalStatus approvalStatus);

    /**
     * Find pending approvals for today (Requirement #2)
     */
    List<RentPaymentTransaction> findByApprovalStatusAndCreatedAtBetween(RentPaymentTransaction.ApprovalStatus approvalStatus, LocalDateTime startOfDay, LocalDateTime endOfDay);

    /**
     * Find all transactions for a coLive up to a specific date-time (Requirement #5)
     */
    List<RentPaymentTransaction> findByColiveOwnerUsernameAndCollectionDateTimeIsBefore(String coliveOwnerUsername, LocalDateTime dateTime);

    /**
     * Find all transactions for a coLive created before a specific date-time (fallback for null collectionDateTime)
     */
    List<RentPaymentTransaction> findByColiveOwnerUsernameAndCreatedAtBefore(String coliveOwnerUsername, LocalDateTime dateTime);

    /**
     * Find all transactions for a specific coLive property created before a specific date-time
     */
    List<RentPaymentTransaction> findByColiveOwnerUsernameAndColiveNameAndCreatedAtBefore(String coliveOwnerUsername, String coliveName, LocalDateTime dateTime);

    /**
     * Find transactions by approval status and coLive
     */
    List<RentPaymentTransaction> findByColiveNameAndApprovalStatus(String coliveName, RentPaymentTransaction.ApprovalStatus approvalStatus);

    /**
     * Find pending approvals for a coLive owner filtered by coliveName
     */
    List<RentPaymentTransaction> findByColiveOwnerUsernameAndColiveNameAndApprovalStatus(String coliveOwnerUsername, String coliveName, RentPaymentTransaction.ApprovalStatus approvalStatus);

    /**
     * Find all unpaid transactions for a tenant
     */
    List<RentPaymentTransaction> findByTenantIdAndStatusIn(String tenantId, List<RentPaymentTransaction.PaymentStatus> statuses);

    /**
     * Find transactions for a tenant by status
     */
    List<RentPaymentTransaction> findByTenantIdAndStatus(String tenantId, RentPaymentTransaction.PaymentStatus status);

    /**
     * Count pending approvals for a coLive
     */
    long countByColiveOwnerUsernameAndApprovalStatus(String coliveOwnerUsername, RentPaymentTransaction.ApprovalStatus approvalStatus);

    /**
     * Find transactions approved by a specific moderator
     */
    List<RentPaymentTransaction> findByApprovedByAndApprovedAtBetween(String approvedBy, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find prorated payments for tenant
     */
    List<RentPaymentTransaction> findByTenantIdAndIsProratedPaymentTrue(String tenantId);

    /**
     * Find all transactions for a specific rent month (for scheduler idempotency)
     */
    List<RentPaymentTransaction> findByRentMonth(LocalDate rentMonth);
}
