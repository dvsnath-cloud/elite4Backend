package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.document.RegistrationDocument;
import com.elite4.anandan.registrationservices.document.TransferRequestDocument;
import com.elite4.anandan.registrationservices.document.TransferRequestDocument.TransferStatus;
import com.elite4.anandan.registrationservices.dto.*;
import com.elite4.anandan.registrationservices.repository.RegistrationRepository;
import com.elite4.anandan.registrationservices.repository.TransferRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransferService {

    private final TransferRequestRepository transferRequestRepository;
    private final RegistrationRepository registrationRepository;
    private final RegistrationService registrationService;

    /**
     * Creates a new transfer request for a tenant.
     * Status starts as PENDING_SOURCE_APPROVAL (old colive moderator must approve first).
     */
    public TransferRequestDocument createTransferRequest(TransferRequestDTO dto) {
        // Validate destination room/house
        if ((dto.getToRoomNumber() == null || dto.getToRoomNumber().isBlank()) &&
                (dto.getToHouseNumber() == null || dto.getToHouseNumber().isBlank())) {
            throw new IllegalArgumentException("Either destination room number or house number must be provided");
        }

        // Validate tenant registration exists
        RegistrationDocument tenant = registrationRepository.findById(dto.getTenantRegistrationId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tenant registration not found: " + dto.getTenantRegistrationId()));

        // Verify tenant is currently OCCUPIED
        if (tenant.getOccupied() != Registration.roomOccupied.OCCUPIED) {
            throw new IllegalArgumentException("Tenant is not currently occupied. Status: " + tenant.getOccupied());
        }

        // Check for existing pending transfer for this tenant
        List<TransferRequestDocument> existing = transferRequestRepository
                .findByTenantRegistrationId(dto.getTenantRegistrationId());
        boolean hasPending = existing.stream()
                .anyMatch(t -> t.getStatus() == TransferStatus.PENDING_SOURCE_APPROVAL
                        || t.getStatus() == TransferStatus.PENDING_DESTINATION_APPROVAL);
        if (hasPending) {
            throw new IllegalArgumentException("A transfer request is already pending for this tenant");
        }

        // Get current user info
        String requestedBy = "SYSTEM";
        String requestedByRole = "ROLE_USER";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            requestedBy = auth.getName();
            requestedByRole = auth.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .filter(a -> a.startsWith("ROLE_"))
                    .findFirst()
                    .orElse("ROLE_USER");
        }

        // Derive source room info from tenant's registration
        String fromRoomNumber = null;
        String fromHouseNumber = null;
        if (tenant.getRoomForRegistration() != null) {
            fromRoomNumber = tenant.getRoomForRegistration().getRoomNumber();
            fromHouseNumber = tenant.getRoomForRegistration().getHouseNumber();
        }

        TransferRequestDocument request = TransferRequestDocument.builder()
                .tenantRegistrationId(dto.getTenantRegistrationId())
                .tenantName(tenant.getFname() + " " + tenant.getLname())
                .tenantContactNo(tenant.getContactNo())
                .tenantEmail(tenant.getEmail())
                .fromColiveUserName(tenant.getColiveUserName())
                .fromColiveName(tenant.getColiveName())
                .fromRoomNumber(fromRoomNumber)
                .fromHouseNumber(fromHouseNumber)
                .toColiveUserName(dto.getToColiveUserName())
                .toColiveName(dto.getToColiveName())
                .toRoomNumber(dto.getToRoomNumber())
                .toHouseNumber(dto.getToHouseNumber())
                .requestedBy(requestedBy)
                .requestedByRole(requestedByRole)
                .requestDate(new Date())
                .status(TransferStatus.PENDING_SOURCE_APPROVAL)
                .build();

        TransferRequestDocument saved = transferRequestRepository.save(request);
        log.info("Transfer request created: {} for tenant {} from {} to {}",
                saved.getId(), tenant.getFname(), tenant.getColiveName(), dto.getToColiveName());
        return saved;
    }

    /**
     * Get pending transfer requests for a moderator.
     * Returns PENDING_SOURCE_APPROVAL where they are the source owner,
     * and PENDING_DESTINATION_APPROVAL where they are the destination owner.
     */
    public List<TransferRequestDocument> getPendingTransfersForModerator(String coliveUserName) {
        List<TransferRequestDocument> sourceApprovals = transferRequestRepository
                .findByStatusAndFromColiveUserName(TransferStatus.PENDING_SOURCE_APPROVAL, coliveUserName);
        List<TransferRequestDocument> destApprovals = transferRequestRepository
                .findByStatusAndToColiveUserName(TransferStatus.PENDING_DESTINATION_APPROVAL, coliveUserName);
        return Stream.concat(sourceApprovals.stream(), destApprovals.stream())
                .collect(Collectors.toList());
    }

    /**
     * Get all transfer requests (any status) for a given moderator.
     */
    public List<TransferRequestDocument> getAllTransfersForModerator(String coliveUserName) {
        return transferRequestRepository
                .findByFromColiveUserNameOrToColiveUserName(coliveUserName, coliveUserName);
    }

    /**
     * Get all pending transfer requests (admin view).
     * Returns both PENDING_SOURCE_APPROVAL and PENDING_DESTINATION_APPROVAL.
     */
    public List<TransferRequestDocument> getAllPendingTransfers() {
        return transferRequestRepository.findByStatusIn(
                List.of(TransferStatus.PENDING_SOURCE_APPROVAL, TransferStatus.PENDING_DESTINATION_APPROVAL));
    }

    /**
     * Get all transfer requests (admin view).
     */
    public List<TransferRequestDocument> getAllTransfers() {
        return transferRequestRepository.findAll();
    }

    /**
     * Get a single transfer request by ID.
     */
    public Optional<TransferRequestDocument> getTransferById(String id) {
        return transferRequestRepository.findById(id);
    }

    // ─── Helper: get current username and check if admin ───

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }

    private boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /**
     * Approve a transfer request. Two-step flow:
     *
     * Step 1 (PENDING_SOURCE_APPROVAL → PENDING_DESTINATION_APPROVAL):
     *   - Caller must be the source colive owner OR an admin.
     *
     * Step 2 (PENDING_DESTINATION_APPROVAL → COMPLETED):
     *   - Caller must be the destination colive owner OR an admin.
     *   - This step performs the actual checkout + re-registration.
     */
    public TransferRequestDocument approveTransfer(String transferId) {
        TransferRequestDocument transfer = transferRequestRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer request not found: " + transferId));

        String currentUser = getCurrentUsername();
        boolean isAdmin = isCurrentUserAdmin();

        if (transfer.getStatus() == TransferStatus.PENDING_SOURCE_APPROVAL) {
            return approveBySource(transfer, currentUser, isAdmin);
        } else if (transfer.getStatus() == TransferStatus.PENDING_DESTINATION_APPROVAL) {
            return approveByDestination(transfer, currentUser, isAdmin);
        } else {
            throw new IllegalArgumentException(
                    "Transfer request is not in a pending approval status. Current: " + transfer.getStatus());
        }
    }

    /**
     * Step 1: Source (old colive) moderator approves.
     * Moves status from PENDING_SOURCE_APPROVAL → PENDING_DESTINATION_APPROVAL.
     */
    private TransferRequestDocument approveBySource(TransferRequestDocument transfer,
                                                     String currentUser, boolean isAdmin) {
        if (!isAdmin && !transfer.getFromColiveUserName().equals(currentUser)) {
            throw new IllegalArgumentException(
                    "Only the source property owner or an admin can approve this step");
        }

        transfer.setStatus(TransferStatus.PENDING_DESTINATION_APPROVAL);
        transfer.setSourceApprovedBy(currentUser);
        transfer.setSourceApprovalDate(new Date());

        TransferRequestDocument saved = transferRequestRepository.save(transfer);
        log.info("Transfer {} source-approved by {}. Now pending destination approval from {}",
                transfer.getId(), currentUser, transfer.getToColiveUserName());
        return saved;
    }

    /**
     * Step 2: Destination (new colive) moderator approves.
     * Performs checkout from source + re-registration at destination, then marks COMPLETED.
     */
    private TransferRequestDocument approveByDestination(TransferRequestDocument transfer,
                                                          String currentUser, boolean isAdmin) {
        if (!isAdmin && !transfer.getToColiveUserName().equals(currentUser)) {
            throw new IllegalArgumentException(
                    "Only the destination property owner or an admin can approve this step");
        }

        // Step 1: Checkout tenant from source property (skip if already vacated)
        RegistrationDocument tenantDoc = registrationRepository.findById(transfer.getTenantRegistrationId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Original tenant registration not found: " + transfer.getTenantRegistrationId()));

        if (tenantDoc.getOccupied() == Registration.roomOccupied.OCCUPIED) {
            UpdateUserForCheckOut checkoutDto = new UpdateUserForCheckOut(
                    transfer.getTenantRegistrationId(), new Date());
            registrationService.checkout(checkoutDto, true);
            log.info("Transfer approval - checked out tenant {} from {}", tenantDoc.getFname(), transfer.getFromColiveName());
        } else {
            log.info("Transfer approval - tenant {} already vacated from {}, skipping checkout",
                    tenantDoc.getFname(), transfer.getFromColiveName());
        }

        // Step 2: Create new registration at destination
        Registration newReg = new Registration();
        newReg.setFname(tenantDoc.getFname());
        newReg.setLname(tenantDoc.getLname());
        newReg.setEmail(tenantDoc.getEmail());
        newReg.setMname(tenantDoc.getMname());
        newReg.setContactNo(tenantDoc.getContactNo());
        newReg.setGender(tenantDoc.getGender());
        newReg.setAddress(tenantDoc.getAddress());
        newReg.setPincode(tenantDoc.getPincode());
        newReg.setDocumentType(tenantDoc.getDocumentType());
        newReg.setDocumentNumber(tenantDoc.getDocumentNumber());
        newReg.setAadharPhotoPath(tenantDoc.getAadharPhotoPath());
        newReg.setDocumentUploadPath(tenantDoc.getDocumentUploadPath());
        newReg.setColiveUserName(transfer.getToColiveUserName());
        newReg.setColiveName(transfer.getToColiveName());
        newReg.setCheckInDate(new Date());
        newReg.setAdvanceAmount(tenantDoc.getAdvanceAmount());
        newReg.setRoomRent(tenantDoc.getRoomRent());
        newReg.setParentName(tenantDoc.getParentName());
        newReg.setParentContactNo(tenantDoc.getParentContactNo());

        RoomForRegistration newRoom = new RoomForRegistration();
        newRoom.setRoomNumber(transfer.getToRoomNumber());
        newRoom.setHouseNumber(transfer.getToHouseNumber());
        if (transfer.getToRoomNumber() != null && !transfer.getToRoomNumber().isBlank()) {
            if (tenantDoc.getRoomForRegistration() != null) {
                newRoom.setRoomType(tenantDoc.getRoomForRegistration().getRoomType());
                newRoom.setRoomCapacity(tenantDoc.getRoomForRegistration().getRoomCapacity());
            }
        } else {
            if (tenantDoc.getRoomForRegistration() != null) {
                newRoom.setHouseType(tenantDoc.getRoomForRegistration().getHouseType());
            }
        }

        RegistrationWithRoomRequest newRegistration = registrationService.create(newReg, newRoom, true);
        log.info("Transfer approval - created new registration {} at {}",
                newRegistration.getId(), transfer.getToColiveName());

        // Step 3: Mark transfer as COMPLETED
        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setDestinationApprovedBy(currentUser);
        transfer.setDestinationApprovalDate(new Date());
        transfer.setNewRegistrationId(newRegistration.getId());
        transfer.setCompletedDate(new Date());

        TransferRequestDocument saved = transferRequestRepository.save(transfer);
        log.info("Transfer {} completed successfully. New registration: {}", transfer.getId(), newRegistration.getId());
        return saved;
    }

    /**
     * Reject a transfer request with a reason.
     * Can be rejected at either PENDING_SOURCE_APPROVAL or PENDING_DESTINATION_APPROVAL stage.
     */
    public TransferRequestDocument rejectTransfer(String transferId, String rejectionReason) {
        TransferRequestDocument transfer = transferRequestRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer request not found: " + transferId));

        if (transfer.getStatus() != TransferStatus.PENDING_SOURCE_APPROVAL
                && transfer.getStatus() != TransferStatus.PENDING_DESTINATION_APPROVAL) {
            throw new IllegalArgumentException(
                    "Transfer request is not in a pending approval status. Current: " + transfer.getStatus());
        }

        String rejectedBy = getCurrentUsername();

        transfer.setStatus(TransferStatus.REJECTED);
        transfer.setRejectedBy(rejectedBy);
        transfer.setRejectionDate(new Date());
        transfer.setRejectionReason(rejectionReason);

        TransferRequestDocument saved = transferRequestRepository.save(transfer);
        log.info("Transfer {} rejected by {}. Reason: {}", transferId, rejectedBy, rejectionReason);
        return saved;
    }
}
