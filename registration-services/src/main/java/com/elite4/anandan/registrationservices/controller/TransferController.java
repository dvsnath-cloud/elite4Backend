package com.elite4.anandan.registrationservices.controller;

import com.elite4.anandan.registrationservices.document.TransferRequestDocument;
import com.elite4.anandan.registrationservices.dto.TransferApprovalDTO;
import com.elite4.anandan.registrationservices.dto.TransferRejectDTO;
import com.elite4.anandan.registrationservices.dto.TransferRequestDTO;
import com.elite4.anandan.registrationservices.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/registrations/transfer")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Slf4j
public class TransferController {

    private final TransferService transferService;

    /**
     * Create a new transfer request.
     * Accessible by ADMIN, MODERATOR, and USER roles.
     */
    @PostMapping("/request")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER')")
    public ResponseEntity<?> createTransferRequest(@Valid @RequestBody TransferRequestDTO dto) {
        try {
            TransferRequestDocument created = transferService.createTransferRequest(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get all pending transfer requests (admin view).
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<List<TransferRequestDocument>> getAllPendingTransfers() {
        return ResponseEntity.ok(transferService.getAllPendingTransfers());
    }

    /**
     * Get pending transfer requests for a specific moderator's properties.
     */
    @GetMapping("/pending/{coliveUserName}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<List<TransferRequestDocument>> getPendingForModerator(
            @PathVariable String coliveUserName,
            @RequestParam(required = false) String coliveName) {
        return ResponseEntity.ok(transferService.getPendingTransfersForModerator(coliveUserName, coliveName));
    }

    /**
     * Get all transfer requests for a specific moderator's properties.
     */
    @GetMapping("/all/{coliveUserName}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<List<TransferRequestDocument>> getAllForModerator(
            @PathVariable String coliveUserName) {
        return ResponseEntity.ok(transferService.getAllTransfersForModerator(coliveUserName));
    }

    /**
     * Get a specific transfer request by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER')")
    public ResponseEntity<TransferRequestDocument> getTransferById(@PathVariable String id) {
        return transferService.getTransferById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Check if a tenant has a pending transfer request.
     * Returns the pending transfer if exists, or 204 No Content if none.
     */
    @GetMapping("/status/tenant/{registrationId}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER')")
    public ResponseEntity<?> getTenantPendingTransfer(@PathVariable String registrationId) {
        var pending = transferService.getPendingTransferForTenant(registrationId);
        return pending
                .map(t -> ResponseEntity.ok((Object) t))
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Approve a transfer request (two-step flow).
     * Step 1: Source colive owner or admin approves → moves to PENDING_DESTINATION_APPROVAL.
     * Step 2: Destination colive owner or admin approves → checkout + re-register → COMPLETED.
     */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> approveTransfer(@PathVariable String id,
                                             @RequestBody(required = false) TransferApprovalDTO approvalDTO) {
        try {
            TransferRequestDocument approved = transferService.approveTransfer(id, approvalDTO);
            return ResponseEntity.ok(approved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reject a transfer request with an optional reason.
     */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> rejectTransfer(@PathVariable String id,
                                            @RequestBody(required = false) TransferRejectDTO rejectDTO) {
        try {
            String reason = rejectDTO != null ? rejectDTO.getRejectionReason() : null;
            TransferRequestDocument rejected = transferService.rejectTransfer(id, reason);
            return ResponseEntity.ok(rejected);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
