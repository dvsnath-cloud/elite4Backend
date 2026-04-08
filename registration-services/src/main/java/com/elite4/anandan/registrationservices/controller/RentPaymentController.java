package com.elite4.anandan.registrationservices.controller;

import com.elite4.anandan.registrationservices.dto.*;
import com.elite4.anandan.registrationservices.service.RentPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rentpayments")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class RentPaymentController {

    private final RentPaymentService rentPaymentService;

    /**
     * Record cash rent payment
     * POST /rentpayments/cash
     */
    @PostMapping("/cash")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER')")
    public ResponseEntity<?> recordCashPayment(@RequestBody CashPaymentRequest request) {
        try {
            log.info("POST /rentpayments/cash - Recording cash payment for tenant: {}", request.getTenantId());
            
            String username = "system"; // In real app, get from JWT token
            PaymentTransactionResponse response = rentPaymentService.recordCashPayment(request, username);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Cash payment recorded successfully");
            result.put("data", response);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error recording cash payment", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Record online rent payment
     * POST /rentpayments/online
     */
    @PostMapping("/online")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER')")
    public ResponseEntity<?> recordOnlinePayment(@RequestBody OnlinePaymentRequest request) {
        try {
            log.info("POST /rentpayments/online - Recording online payment for tenant: {}", request.getTenantId());
            
            String username = "system"; // In real app, get from JWT token
            PaymentTransactionResponse response = rentPaymentService.recordOnlinePayment(request, username);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Online payment recorded successfully");
            result.put("data", response);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error recording online payment", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get tenant payment history for last 12 months
     * GET /rentpayments/tenant/{tenantId}/history
     */
    @GetMapping("/tenant/{tenantId}/history")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER')")
    public ResponseEntity<?> getTenantPaymentHistory(@PathVariable String tenantId) {
        try {
            log.info("GET /rentpayments/tenant/{}/history", tenantId);
            
            List<TenantPaymentHistoryItem> history = rentPaymentService.getTenantPaymentHistory(tenantId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Payment history retrieved successfully");
            result.put("totalRecords", history.size());
            result.put("data", history);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching tenant payment history", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Consolidated payment summary for a tenant — single API call.
     * Returns current month status, first-time detection, prorated info,
     * outstanding balance, and last 6 months payment history.
     * GET /rentpayments/tenant/{tenantId}/payment-summary
     */
    @GetMapping("/tenant/{tenantId}/payment-summary")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER')")
    public ResponseEntity<?> getTenantPaymentSummary(@PathVariable String tenantId) {
        try {
            log.info("GET /rentpayments/tenant/{}/payment-summary", tenantId);

            TenantPaymentSummary summary = rentPaymentService.getTenantPaymentSummary(tenantId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Payment summary retrieved successfully");
            result.put("data", summary);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching tenant payment summary", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get owner's payment dashboard for current month
     * GET /rentpayments/owner/{username}/dashboard
     */
    @GetMapping("/owner/{username}/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> getOwnerPaymentDashboard(
            @PathVariable String username,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        try {
            log.info("GET /rentpayments/owner/{}/dashboard", username);
            
            LocalDate queryMonth = month != null ? month : LocalDate.now();
            OwnerPaymentDashboard dashboard = rentPaymentService.getOwnerPaymentDashboard(username, queryMonth);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Payment dashboard retrieved successfully");
            result.put("data", dashboard);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching owner payment dashboard", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get owner's payment history for last 6 months
     * GET /rentpayments/owner/{username}/history
     */
    @GetMapping("/owner/{username}/history")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> getOwnerPaymentHistory(@PathVariable String username) {
        try {
            log.info("GET /rentpayments/owner/{}/history", username);
            
            List<OwnerPaymentDashboard> history = rentPaymentService.getOwnerPaymentHistory(username);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Payment history retrieved successfully");
            result.put("totalMonths", history.size());
            result.put("data", history);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching owner payment history", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get owner's bank details (for tenant to know where to transfer)
     * GET /rentpayments/owner/{username}/bankdetails
     * Query params: coliveName (required)
     */
    @GetMapping("/owner/{username}/bankdetails")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER')")
    public ResponseEntity<?> getOwnerBankDetails(@PathVariable String username, 
                                                 @RequestParam(value = "coliveName", required = true) String coliveName) {
        try {
            log.info("GET /rentpayments/owner/{}/bankdetails?coliveName={}", username, coliveName);
            
            if (coliveName == null || coliveName.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "coliveName parameter is required");
                return ResponseEntity.badRequest().body(error);
            }
            
            Map<String, Object> result = rentPaymentService.getBankDetailsByColiveAndRoomOwner(username, coliveName);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching owner bank details", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * (Requirement #3) Record prorated/partial month cash payment
     * POST /rentpayments/prorated-cash
     */
    @PostMapping("/prorated-cash")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER')")
    public ResponseEntity<?> recordProratedCashPayment(@RequestBody ProratedCashPaymentRequest request) {
        try {
            log.info("POST /rentpayments/prorated-cash - Recording prorated payment for tenant: {}", request.getTenantId());
            
            String username = "system";
            PaymentTransactionResponse response = rentPaymentService.recordProratedCashPayment(request, username);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Prorated payment recorded successfully (awaiting moderator approval)");
            result.put("data", response);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error recording prorated payment", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * (Requirement #2) Get pending cash payment approvals for moderator
     * GET /rentpayments/owner/{username}/pending-approvals
     */
    @GetMapping("/owner/{username}/pending-approvals")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> getPendingPaymentApprovals(@PathVariable String username,
            @RequestParam(required = false) String coliveName) {
        try {
            log.info("GET /rentpayments/owner/{}/pending-approvals?coliveName={}", username, coliveName);
            
            List<PendingPaymentApprovalItem> approvals = rentPaymentService.getPendingPaymentApprovals(username, coliveName);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Pending approvals retrieved successfully");
            result.put("totalPending", approvals.size());
            result.put("data", approvals);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching pending approvals", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * (Requirement #2) Approve or reject a cash payment
     * POST /rentpayments/approve-reject
     */
    @PostMapping("/approve-reject")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> approveRejectPayment(@RequestBody PaymentApprovalRequest request) {
        try {
            log.info("POST /rentpayments/approve-reject - Processing approval for transaction: {}", request.getTransactionId());
            
            String moderatorUsername = "system";  // In real app, get from JWT token
            PaymentTransactionResponse response = rentPaymentService.approveRejectPayment(request, moderatorUsername);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", request.getApprove() ? "Payment approved successfully" : "Payment rejected successfully");
            result.put("data", response);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error processing payment approval", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * (Requirement #4) Get tenant's outstanding balance for all months and properties
     * GET /rentpayments/tenant/{tenantId}/outstanding-balance
     */
    @GetMapping("/tenant/{tenantId}/outstanding-balance")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER')")
    public ResponseEntity<?> getTenantOutstandingBalance(@PathVariable String tenantId) {
        try {
            log.info("GET /rentpayments/tenant/{}/outstanding-balance", tenantId);
            
            TenantOutstandingBalance balance = rentPaymentService.getTenantOutstandingBalance(tenantId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Outstanding balance retrieved successfully");
            result.put("data", balance);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching outstanding balance", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * (Requirement #5) Get moderator's collection report for a specific month (YYYY-MM format)
     * GET /rentpayments/owner/{username}/collection-report?month=YYYY-MM
     */
    @GetMapping("/owner/{username}/collection-report")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> getModeratorCollectionReport(
            @PathVariable String username,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String coliveName) {
        try {
            log.info("GET /rentpayments/owner/{}/collection-report month={} coliveName={}", username, month, coliveName);
            
            // Default to current month if not specified
            String reportMonth = month != null ? month : java.time.YearMonth.now().toString();
            ModeratorCollectionReport report = rentPaymentService.getMonthlyCollectionStatus(username, reportMonth, coliveName);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Collection report generated successfully");
            result.put("data", report);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error generating collection report", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * (UI Integration) Get monthly collection status for moderator
     * Returns collection data for the specified month and property
     * GET /rentpayments/owner/{username}/monthly-collection-status?month={YYYY-MM}&coliveName={optional}
     */
    @GetMapping("/owner/{username}/monthly-collection-status")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> getMonthlyCollectionStatus(
            @PathVariable String username,
            @RequestParam(required = true) String month,
            @RequestParam(required = false) String coliveName) {
        try {
            log.info("GET /rentpayments/owner/{}/monthly-collection-status month={} coliveName={}", username, month, coliveName);
            
            ModeratorCollectionReport report = rentPaymentService.getMonthlyCollectionStatus(username, month, coliveName);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Monthly collection status retrieved successfully");
            result.put("data", report);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error retrieving monthly collection status", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
