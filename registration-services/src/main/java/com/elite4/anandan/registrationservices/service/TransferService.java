package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.document.RegistrationDocument;
import com.elite4.anandan.registrationservices.document.TransferRequestDocument;
import com.elite4.anandan.registrationservices.document.TransferRequestDocument.TransferStatus;
import com.elite4.anandan.registrationservices.dto.*;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.RegistrationRepository;
import com.elite4.anandan.registrationservices.repository.TransferRequestRepository;
import com.elite4.anandan.registrationservices.repository.UserRepository;
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
    private final NotificationClient notificationClient;
    private final UserRepository userRepository;

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

        // Update transferStatus on the registration document
        tenant.setTransferStatus("TRANSFER_PENDING");
        registrationRepository.save(tenant);

        // --- Notification: Transfer request created ---
        try {
            String fromRoom = saved.getFromRoomNumber() != null ? saved.getFromRoomNumber() : saved.getFromHouseNumber();
            String toRoom = saved.getToRoomNumber() != null ? saved.getToRoomNumber() : saved.getToHouseNumber();

            // Notify tenant
            String tenantMsg = "Dear " + saved.getTenantName() + ", a transfer request has been created to move you from "
                    + saved.getFromColiveName() + " (Room: " + fromRoom + ") to " + saved.getToColiveName() + " (Room: " + toRoom
                    + "). Awaiting approval from property owners.";
            if (tenant.getEmail() != null && !tenant.getEmail().isBlank()) {
                notificationClient.sendEmail(tenant.getEmail(), "Transfer Request Created - CoLives Connect", tenantMsg);
            }
            if (tenant.getContactNo() != null && !tenant.getContactNo().isBlank()) {
                notificationClient.sendSms(tenant.getContactNo(), tenantMsg);
                notificationClient.sendWhatsapp(tenant.getContactNo(), tenantMsg);
            }

            // Notify source colive owner (approval needed)
            Optional<User> sourceOwner = userRepository.findByUsername(saved.getFromColiveUserName());
            if (sourceOwner.isPresent()) {
                User owner = sourceOwner.get();
                String ownerMsg = "A transfer request requires your approval. Tenant " + saved.getTenantName()
                        + " wants to transfer from your property " + saved.getFromColiveName() + " (Room: " + fromRoom
                        + ") to " + saved.getToColiveName() + ". Please review and approve/reject.";
                if (owner.getEmail() != null && !owner.getEmail().isBlank()) {
                    notificationClient.sendEmail(owner.getEmail(), "Transfer Approval Required - CoLives Connect", ownerMsg);
                }
                if (owner.getPhoneRaw() != null && !owner.getPhoneRaw().isBlank()) {
                    notificationClient.sendSms(owner.getPhoneRaw(), ownerMsg);
                    notificationClient.sendWhatsapp(owner.getPhoneRaw(), ownerMsg);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to send transfer request notifications: {}", ex.getMessage());
        }

        return saved;
    }

    /**
     * Get pending transfer requests for a moderator.
     * Returns PENDING_SOURCE_APPROVAL where they are the source owner,
     * and PENDING_DESTINATION_APPROVAL where they are the destination owner.
     * Optionally filtered by coliveName.
     */
    public List<TransferRequestDocument> getPendingTransfersForModerator(String coliveUserName, String coliveName) {
        List<TransferRequestDocument> sourceApprovals;
        List<TransferRequestDocument> destApprovals;
        if (coliveName != null && !coliveName.isBlank()) {
            sourceApprovals = transferRequestRepository
                    .findByStatusAndFromColiveUserNameAndFromColiveName(TransferStatus.PENDING_SOURCE_APPROVAL, coliveUserName, coliveName);
            destApprovals = transferRequestRepository
                    .findByStatusAndToColiveUserNameAndToColiveName(TransferStatus.PENDING_DESTINATION_APPROVAL, coliveUserName, coliveName);
        } else {
            sourceApprovals = transferRequestRepository
                    .findByStatusAndFromColiveUserName(TransferStatus.PENDING_SOURCE_APPROVAL, coliveUserName);
            destApprovals = transferRequestRepository
                    .findByStatusAndToColiveUserName(TransferStatus.PENDING_DESTINATION_APPROVAL, coliveUserName);
        }
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

    /**
     * Get any pending (PENDING_SOURCE_APPROVAL or PENDING_DESTINATION_APPROVAL) transfer for a tenant.
     */
    public Optional<TransferRequestDocument> getPendingTransferForTenant(String registrationId) {
        return transferRequestRepository.findByTenantRegistrationId(registrationId).stream()
                .filter(t -> t.getStatus() == TransferRequestDocument.TransferStatus.PENDING_SOURCE_APPROVAL
                        || t.getStatus() == TransferRequestDocument.TransferStatus.PENDING_DESTINATION_APPROVAL)
                .findFirst();
    }

    // ─── Helper: get current username and check if admin ───

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "SYSTEM";
        String authName = auth.getName();
        // auth.getName() may return phone, email, or username — resolve to actual username
        Optional<User> user = userRepository.findByUsername(authName);
        if (user.isEmpty()) {
            user = userRepository.findByPhoneE164(authName);
        }
        if (user.isEmpty()) {
            user = userRepository.findByPhoneRaw(authName);
        }
        if (user.isEmpty()) {
            user = userRepository.findByEmail(authName);
        }
        return user.map(User::getUsername).orElse(authName);
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
    public TransferRequestDocument approveTransfer(String transferId, TransferApprovalDTO approvalDTO) {
        TransferRequestDocument transfer = transferRequestRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer request not found: " + transferId));

        String currentUser = getCurrentUsername();
        boolean isAdmin = isCurrentUserAdmin();

        if (transfer.getStatus() == TransferStatus.PENDING_SOURCE_APPROVAL) {
            return approveBySource(transfer, currentUser, isAdmin);
        } else if (transfer.getStatus() == TransferStatus.PENDING_DESTINATION_APPROVAL) {
            return approveByDestination(transfer, currentUser, isAdmin, approvalDTO);
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
        log.info("approveBySource: currentUser='{}', fromColiveUserName='{}', isAdmin={}",
                currentUser, transfer.getFromColiveUserName(), isAdmin);
        if (!isAdmin && !transfer.getFromColiveUserName().equals(currentUser)) {
            throw new IllegalArgumentException(
                    "Only the source property owner or an admin can approve this step. Current user: "
                    + currentUser + ", expected: " + transfer.getFromColiveUserName());
        }

        transfer.setStatus(TransferStatus.PENDING_DESTINATION_APPROVAL);
        transfer.setSourceApprovedBy(currentUser);
        transfer.setSourceApprovalDate(new Date());

        TransferRequestDocument saved = transferRequestRepository.save(transfer);
        log.info("Transfer {} source-approved by {}. Now pending destination approval from {}",
                transfer.getId(), currentUser, transfer.getToColiveUserName());

        // --- Notification: Source approved, notify destination owner ---
        try {
            String toRoom = saved.getToRoomNumber() != null ? saved.getToRoomNumber() : saved.getToHouseNumber();
            Optional<User> destOwner = userRepository.findByUsername(saved.getToColiveUserName());
            if (destOwner.isPresent()) {
                User owner = destOwner.get();
                String msg = "A tenant transfer requires your approval. Tenant " + saved.getTenantName()
                        + " is transferring from " + saved.getFromColiveName() + " to your property " + saved.getToColiveName()
                        + " (Room: " + toRoom + "). Source property has approved. Please review and approve/reject.";
                if (owner.getEmail() != null && !owner.getEmail().isBlank()) {
                    notificationClient.sendEmail(owner.getEmail(), "Transfer Approval Required - CoLives Connect", msg);
                }
                if (owner.getPhoneRaw() != null && !owner.getPhoneRaw().isBlank()) {
                    notificationClient.sendSms(owner.getPhoneRaw(), msg);
                    notificationClient.sendWhatsapp(owner.getPhoneRaw(), msg);
                }
            }
            // Notify tenant of progress
            String tenantMsg = "Dear " + saved.getTenantName() + ", your transfer request from " + saved.getFromColiveName()
                    + " to " + saved.getToColiveName() + " has been approved by the source property. Awaiting destination property approval.";
            if (saved.getTenantEmail() != null && !saved.getTenantEmail().isBlank()) {
                notificationClient.sendEmail(saved.getTenantEmail(), "Transfer Progress Update - CoLives Connect", tenantMsg);
            }
            if (saved.getTenantContactNo() != null && !saved.getTenantContactNo().isBlank()) {
                notificationClient.sendSms(saved.getTenantContactNo(), tenantMsg);
                notificationClient.sendWhatsapp(saved.getTenantContactNo(), tenantMsg);
            }
        } catch (Exception ex) {
            log.warn("Failed to send source approval notifications: {}", ex.getMessage());
        }

        return saved;
    }

    /**
     * Step 2: Destination (new colive) moderator approves.
     * Updates the existing registration in-place with new colive/room details (no duplicate record).
     * Frees the old room and occupies the new room via RegistrationService.
     */
    private TransferRequestDocument approveByDestination(TransferRequestDocument transfer,
                                                          String currentUser, boolean isAdmin,
                                                          TransferApprovalDTO approvalDTO) {
        log.info("approveByDestination: currentUser='{}', toColiveUserName='{}', isAdmin={}",
                currentUser, transfer.getToColiveUserName(), isAdmin);
        if (!isAdmin && !transfer.getToColiveUserName().equals(currentUser)) {
            throw new IllegalArgumentException(
                    "Only the destination property owner or an admin can approve this step. Current user: "
                    + currentUser + ", expected: " + transfer.getToColiveUserName());
        }

        // Fetch the existing tenant registration
        RegistrationDocument tenantDoc = registrationRepository.findById(transfer.getTenantRegistrationId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Original tenant registration not found: " + transfer.getTenantRegistrationId()));

        // Apply overrides from destination approver if provided
        double finalRoomRent = (approvalDTO != null && approvalDTO.getRoomRent() != null)
                ? approvalDTO.getRoomRent() : tenantDoc.getRoomRent();
        double finalAdvanceAmount = (approvalDTO != null && approvalDTO.getAdvanceAmount() != null)
                ? approvalDTO.getAdvanceAmount() : tenantDoc.getAdvanceAmount();
        String finalToRoomNumber = (approvalDTO != null && approvalDTO.getToRoomNumber() != null && !approvalDTO.getToRoomNumber().isBlank())
                ? approvalDTO.getToRoomNumber() : transfer.getToRoomNumber();
        String finalToHouseNumber = (approvalDTO != null && approvalDTO.getToHouseNumber() != null && !approvalDTO.getToHouseNumber().isBlank())
                ? approvalDTO.getToHouseNumber() : transfer.getToHouseNumber();

        // Step 1: Free the old room occupancy
        registrationService.freeRoomOccupancy(tenantDoc);

        // Step 2: Update the existing registration in-place with new destination details
        tenantDoc.setColiveUserName(transfer.getToColiveUserName());
        tenantDoc.setColiveName(transfer.getToColiveName());
        tenantDoc.setCheckInDate(new Date());
        tenantDoc.setCheckOutDate(null);
        tenantDoc.setRoomRent(finalRoomRent);
        tenantDoc.setAdvanceAmount(finalAdvanceAmount);
        tenantDoc.setOccupied(Registration.roomOccupied.OCCUPIED);
        tenantDoc.setActive(Boolean.TRUE);
        tenantDoc.setTransferStatus(null);

        // Update room info
        RoomForRegistration newRoom = new RoomForRegistration();
        newRoom.setRoomNumber(finalToRoomNumber);
        newRoom.setHouseNumber(finalToHouseNumber);
        if (finalToRoomNumber != null && !finalToRoomNumber.isBlank()) {
            if (tenantDoc.getRoomForRegistration() != null) {
                newRoom.setRoomType(tenantDoc.getRoomForRegistration().getRoomType());
                newRoom.setRoomCapacity(tenantDoc.getRoomForRegistration().getRoomCapacity());
            }
        } else {
            if (tenantDoc.getRoomForRegistration() != null) {
                newRoom.setHouseType(tenantDoc.getRoomForRegistration().getHouseType());
            }
        }
        tenantDoc.setRoomForRegistration(newRoom);

        // Step 3: Occupy the new room
        registrationService.occupyRoom(tenantDoc);

        // Save the updated registration
        RegistrationDocument savedDoc = registrationRepository.save(tenantDoc);
        log.info("Transfer approval - updated registration {} from {} to {}",
                savedDoc.getId(), transfer.getFromColiveName(), transfer.getToColiveName());

        // Step 4: Mark transfer as COMPLETED
        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setDestinationApprovedBy(currentUser);
        transfer.setDestinationApprovalDate(new Date());
        transfer.setNewRegistrationId(savedDoc.getId());
        transfer.setCompletedDate(new Date());

        TransferRequestDocument saved = transferRequestRepository.save(transfer);
        log.info("Transfer {} completed successfully. Registration {} updated in-place.", transfer.getId(), savedDoc.getId());

        // --- Notification: Transfer completed ---
        try {
            String toRoom = finalToRoomNumber != null ? finalToRoomNumber : finalToHouseNumber;
            String fromRoom = saved.getFromRoomNumber() != null ? saved.getFromRoomNumber() : saved.getFromHouseNumber();

            // Notify tenant
            String tenantMsg = "Dear " + saved.getTenantName() + ", your transfer from " + saved.getFromColiveName()
                    + " (Room: " + fromRoom + ") to " + saved.getToColiveName() + " (Room: " + toRoom
                    + ") has been completed successfully. Welcome to your new room!";
            if (saved.getTenantEmail() != null && !saved.getTenantEmail().isBlank()) {
                notificationClient.sendEmail(saved.getTenantEmail(), "Transfer Completed - CoLives Connect", tenantMsg);
            }
            if (saved.getTenantContactNo() != null && !saved.getTenantContactNo().isBlank()) {
                notificationClient.sendSms(saved.getTenantContactNo(), tenantMsg);
                notificationClient.sendWhatsapp(saved.getTenantContactNo(), tenantMsg);
            }

            // Notify source owner
            Optional<User> srcOwner = userRepository.findByUsername(saved.getFromColiveUserName());
            if (srcOwner.isPresent()) {
                User owner = srcOwner.get();
                String srcMsg = "Tenant " + saved.getTenantName() + " has been transferred out of your property "
                        + saved.getFromColiveName() + " (Room: " + fromRoom + "). Room is now available.";
                if (owner.getEmail() != null && !owner.getEmail().isBlank()) {
                    notificationClient.sendEmail(owner.getEmail(), "Tenant Transferred Out - CoLives Connect", srcMsg);
                }
                if (owner.getPhoneRaw() != null && !owner.getPhoneRaw().isBlank()) {
                    notificationClient.sendSms(owner.getPhoneRaw(), srcMsg);
                    notificationClient.sendWhatsapp(owner.getPhoneRaw(), srcMsg);
                }
            }

            // Notify destination owner
            Optional<User> destOwner = userRepository.findByUsername(saved.getToColiveUserName());
            if (destOwner.isPresent()) {
                User owner = destOwner.get();
                String destMsg = "Tenant " + saved.getTenantName() + " has been successfully transferred to your property "
                        + saved.getToColiveName() + " (Room: " + toRoom + ").";
                if (owner.getEmail() != null && !owner.getEmail().isBlank()) {
                    notificationClient.sendEmail(owner.getEmail(), "New Tenant Transferred In - CoLives Connect", destMsg);
                }
                if (owner.getPhoneRaw() != null && !owner.getPhoneRaw().isBlank()) {
                    notificationClient.sendSms(owner.getPhoneRaw(), destMsg);
                    notificationClient.sendWhatsapp(owner.getPhoneRaw(), destMsg);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to send transfer completion notifications: {}", ex.getMessage());
        }

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

        // Clear transferStatus on the tenant's registration
        registrationRepository.findById(transfer.getTenantRegistrationId()).ifPresent(tenant -> {
            tenant.setTransferStatus(null);
            registrationRepository.save(tenant);
        });

        // --- Notification: Transfer rejected ---
        try {
            // Notify tenant
            String tenantMsg = "Dear " + saved.getTenantName() + ", your transfer request from " + saved.getFromColiveName()
                    + " to " + saved.getToColiveName() + " has been rejected."
                    + (rejectionReason != null ? " Reason: " + rejectionReason : "");
            if (saved.getTenantEmail() != null && !saved.getTenantEmail().isBlank()) {
                notificationClient.sendEmail(saved.getTenantEmail(), "Transfer Request Rejected - CoLives Connect", tenantMsg);
            }
            if (saved.getTenantContactNo() != null && !saved.getTenantContactNo().isBlank()) {
                notificationClient.sendSms(saved.getTenantContactNo(), tenantMsg);
                notificationClient.sendWhatsapp(saved.getTenantContactNo(), tenantMsg);
            }

            // Notify both property owners
            for (String ownerUsername : List.of(saved.getFromColiveUserName(), saved.getToColiveUserName())) {
                if (ownerUsername != null) {
                    Optional<User> ownerOpt = userRepository.findByUsername(ownerUsername);
                    if (ownerOpt.isPresent()) {
                        User owner = ownerOpt.get();
                        String ownerMsg = "Transfer request for tenant " + saved.getTenantName() + " from "
                                + saved.getFromColiveName() + " to " + saved.getToColiveName() + " has been rejected by " + rejectedBy + "."
                                + (rejectionReason != null ? " Reason: " + rejectionReason : "");
                        if (owner.getEmail() != null && !owner.getEmail().isBlank()) {
                            notificationClient.sendEmail(owner.getEmail(), "Transfer Request Rejected - CoLives Connect", ownerMsg);
                        }
                        if (owner.getPhoneRaw() != null && !owner.getPhoneRaw().isBlank()) {
                            notificationClient.sendSms(owner.getPhoneRaw(), ownerMsg);
                            notificationClient.sendWhatsapp(owner.getPhoneRaw(), ownerMsg);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to send transfer rejection notifications: {}", ex.getMessage());
        }

        return saved;
    }
}
