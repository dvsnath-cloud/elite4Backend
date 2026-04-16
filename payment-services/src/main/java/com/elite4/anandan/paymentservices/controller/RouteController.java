package com.elite4.anandan.paymentservices.controller;

import com.elite4.anandan.paymentservices.document.LinkedAccountDocument;
import com.elite4.anandan.paymentservices.document.PaymentRefundDocument;
import com.elite4.anandan.paymentservices.document.PaymentTransferDocument;
import com.elite4.anandan.paymentservices.dto.*;
import com.elite4.anandan.paymentservices.service.LinkedAccountService;
import com.elite4.anandan.paymentservices.service.RefundService;
import com.elite4.anandan.paymentservices.service.RouteService;
import com.elite4.anandan.paymentservices.service.SettlementReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/payments/route")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class RouteController {

    private final LinkedAccountService linkedAccountService;
    private final RouteService routeService;
    private final SettlementReportService settlementReportService;
    private final RefundService refundService;

    // ── Linked Accounts (Phase 1) ──────────────────────────────────

    @PostMapping("/linked-accounts")
    public ResponseEntity<?> createLinkedAccount(@RequestBody LinkedAccountRequest request) {
        log.info("POST /payments/route/linked-accounts → owner={}, colive={}, bank={}",
                request.getOwnerUsername(), request.getColiveName(), request.getBankName());

        if (request.getOwnerUsername() == null || request.getOwnerUsername().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "ownerUsername is required"));
        }
        if (request.getColiveName() == null || request.getColiveName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "coliveName is required"));
        }
        if (request.getAccountNumber() == null || request.getAccountNumber().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "accountNumber is required"));
        }
        if (request.getIfscCode() == null || request.getIfscCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "ifscCode is required"));
        }

        LinkedAccountResponse response = linkedAccountService.createLinkedAccount(request);

        if ("FAILED".equals(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/linked-accounts")
    public ResponseEntity<List<LinkedAccountDocument>> getLinkedAccounts(
            @RequestParam String ownerUsername,
            @RequestParam String coliveName) {
        log.info("GET /payments/route/linked-accounts → owner={}, colive={}", ownerUsername, coliveName);
        List<LinkedAccountDocument> accounts = linkedAccountService.getAccountsByOwnerAndColive(ownerUsername, coliveName);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/linked-accounts/by-owner")
    public ResponseEntity<List<LinkedAccountDocument>> getLinkedAccountsByOwner(
            @RequestParam String ownerUsername,
            @RequestParam(required = false) String coliveName) {
        log.info("GET /payments/route/linked-accounts/by-owner → owner={}, colive={}", ownerUsername, coliveName);
        List<LinkedAccountDocument> accounts = (coliveName != null && !coliveName.isBlank())
                ? linkedAccountService.getAccountsByOwnerAndColive(ownerUsername, coliveName)
                : linkedAccountService.getAccountsByOwner(ownerUsername);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/linked-accounts/primary")
    public ResponseEntity<?> getPrimaryAccount(
            @RequestParam String ownerUsername,
            @RequestParam String coliveName) {
        log.info("GET /payments/route/linked-accounts/primary → owner={}, colive={}", ownerUsername, coliveName);
        Optional<LinkedAccountDocument> primary = linkedAccountService.getPrimaryAccount(ownerUsername, coliveName);
        if (primary.isEmpty()) {
            return ResponseEntity.ok(Map.of("found", false, "message", "No primary bank account configured"));
        }
        LinkedAccountDocument acc = primary.get();
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("found", true);
        response.put("account", acc);
        response.put("razorpaySynced", acc.isRazorpaySynced());
        response.put("activationStatus", acc.getActivationStatus() != null ? acc.getActivationStatus() : "unknown");
        response.put("kycStatus", acc.getKycStatus() != null ? acc.getKycStatus() : "unknown");
        response.put("razorpayAccountId", acc.getRazorpayAccountId());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/linked-accounts/{accountId}/set-primary")
    public ResponseEntity<?> setPrimary(@PathVariable String accountId) {
        log.info("PUT /payments/route/linked-accounts/{}/set-primary", accountId);
        try {
            LinkedAccountResponse response = linkedAccountService.setPrimary(accountId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/linked-accounts/{accountId}")
    public ResponseEntity<?> deleteAccount(@PathVariable String accountId) {
        log.info("DELETE /payments/route/linked-accounts/{}", accountId);
        try {
            linkedAccountService.deleteAccount(accountId);
            return ResponseEntity.ok(Map.of("message", "Bank account deleted successfully."));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ── KYC / Onboarding (Phase 2) ────────────────────────────────

    @PostMapping("/linked-accounts/{accountId}/submit-kyc")
    public ResponseEntity<?> submitKyc(@PathVariable String accountId,
                                       @RequestBody KycSubmissionRequest request) {
        log.info("POST /payments/route/linked-accounts/{}/submit-kyc", accountId);
        try {
            LinkedAccountResponse response = linkedAccountService.submitKyc(accountId, request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/linked-accounts/{accountId}/kyc-status")
    public ResponseEntity<?> getKycStatus(@PathVariable String accountId) {
        log.info("GET /payments/route/linked-accounts/{}/kyc-status", accountId);
        try {
            LinkedAccountResponse response = linkedAccountService.fetchKycStatus(accountId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping(value = "/linked-accounts/{accountId}/upload-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(
            @PathVariable String accountId,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) {
        log.info("POST /payments/route/linked-accounts/{}/upload-document → type={}, file={}",
                accountId, documentType, file.getOriginalFilename());

        if (documentType == null || documentType.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "documentType is required"));
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "file is required"));
        }

        // Validate document type
        List<String> supported = linkedAccountService.getSupportedDocumentTypes();
        if (!supported.contains(documentType)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Unsupported document type: " + documentType,
                    "supportedTypes", supported));
        }

        // Validate file size (max 5MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("message", "File size exceeds 5MB limit"));
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !(contentType.startsWith("image/") || "application/pdf".equals(contentType))) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only image files (JPEG, PNG) and PDF are accepted"));
        }

        try {
            LinkedAccountResponse response = linkedAccountService.uploadDocument(accountId, documentType, file);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/linked-accounts/document-types")
    public ResponseEntity<List<String>> getSupportedDocumentTypes() {
        log.info("GET /payments/route/linked-accounts/document-types");
        return ResponseEntity.ok(linkedAccountService.getSupportedDocumentTypes());
    }

    // ── Transfers (Phase 1) ────────────────────────────────────────

    @GetMapping("/transfers")
    public ResponseEntity<List<PaymentTransferDocument>> getTransfers(@RequestParam String ownerUsername) {
        log.info("GET /payments/route/transfers → owner={}", ownerUsername);
        List<PaymentTransferDocument> transfers = routeService.getTransfersByOwner(ownerUsername);
        return ResponseEntity.ok(transfers);
    }

    // ── Razorpay Sync Retry ────────────────────────────────────────

    @PostMapping("/linked-accounts/retry-sync")
    public ResponseEntity<?> retrySyncUnsynced() {
        log.info("POST /payments/route/linked-accounts/retry-sync — manual trigger");
        int synced = linkedAccountService.retryUnsyncedAccounts();
        return ResponseEntity.ok(Map.of(
                "message", "Retry complete",
                "syncedCount", synced,
                "remainingUnsynced", linkedAccountService.getUnsyncedAccounts().size()
        ));
    }

    @GetMapping("/linked-accounts/unsynced")
    public ResponseEntity<?> getUnsyncedAccounts() {
        log.info("GET /payments/route/linked-accounts/unsynced");
        List<LinkedAccountDocument> unsynced = linkedAccountService.getUnsyncedAccounts();
        return ResponseEntity.ok(Map.of(
                "count", unsynced.size(),
                "accounts", unsynced
        ));
    }

    @GetMapping("/transfers/{paymentId}")
    public ResponseEntity<?> getTransferByPaymentId(@PathVariable String paymentId) {
        log.info("GET /payments/route/transfers/{}", paymentId);
        Optional<PaymentTransferDocument> transfer = routeService.getTransferByPaymentId(paymentId);
        if (transfer.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(transfer.get());
    }

    // ── Settlement Reports (Phase 3) ──────────────────────────────

    @GetMapping("/reports/owner-settlements")
    public ResponseEntity<?> getOwnerSettlementReport(
            @RequestParam String ownerUsername,
            @RequestParam int year,
            @RequestParam int month) {
        log.info("GET /payments/route/reports/owner-settlements → owner={}, {}-{}", ownerUsername, year, month);
        SettlementReport report = settlementReportService.getOwnerSettlementReport(ownerUsername, year, month);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/reports/colive-settlements")
    public ResponseEntity<?> getColiveSettlementReport(
            @RequestParam String ownerUsername,
            @RequestParam String coliveName,
            @RequestParam int year,
            @RequestParam int month) {
        log.info("GET /payments/route/reports/colive-settlements → owner={}, colive={}, {}-{}",
                ownerUsername, coliveName, year, month);
        SettlementReport report = settlementReportService.getColiveSettlementReport(ownerUsername, coliveName, year, month);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/reports/platform-earnings")
    public ResponseEntity<?> getPlatformEarningsReport(
            @RequestParam int year,
            @RequestParam int month) {
        log.info("GET /payments/route/reports/platform-earnings → {}-{}", year, month);
        PlatformEarningsReport report = settlementReportService.getPlatformEarningsReport(year, month);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/reports/reconciliation")
    public ResponseEntity<?> getReconciliation(@RequestParam String ownerUsername) {
        log.info("GET /payments/route/reports/reconciliation → owner={}", ownerUsername);
        Map<String, Object> summary = settlementReportService.getReconciliationSummary(ownerUsername);
        return ResponseEntity.ok(summary);
    }

    // ── Refunds (Phase 3) ─────────────────────────────────────────

    @PostMapping("/refunds")
    public ResponseEntity<?> initiateRefund(@RequestBody RefundRequest request) {
        log.info("POST /payments/route/refunds → paymentId={}, amount={}", request.getRazorpayPaymentId(), request.getAmount());
        if (request.getRazorpayPaymentId() == null || request.getRazorpayPaymentId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "razorpayPaymentId is required"));
        }
        RefundResponse response = refundService.initiateRefund(request);
        if ("FAILED".equals(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/refunds/{paymentId}")
    public ResponseEntity<List<PaymentRefundDocument>> getRefundsByPaymentId(@PathVariable String paymentId) {
        log.info("GET /payments/route/refunds/{}", paymentId);
        return ResponseEntity.ok(refundService.getRefundsByPaymentId(paymentId));
    }

    @GetMapping("/refunds")
    public ResponseEntity<List<PaymentRefundDocument>> getRefundsByOwner(@RequestParam String ownerUsername) {
        log.info("GET /payments/route/refunds → owner={}", ownerUsername);
        return ResponseEntity.ok(refundService.getRefundsByOwner(ownerUsername));
    }

    // ── Config ─────────────────────────────────────────────────────

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getRouteConfig() {
        log.info("GET /payments/route/config");
        return ResponseEntity.ok(Map.of(
                "routeEnabled", routeService.isRouteEnabled(),
                "platformFee", routeService.getPlatformFee(),
                "platformFeeDisplay", "₹" + (routeService.getPlatformFee() / 100.0)
        ));
    }
}
