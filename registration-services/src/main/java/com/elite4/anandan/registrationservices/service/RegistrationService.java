package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.document.RegistrationDocument;
import com.elite4.anandan.registrationservices.document.RoomOnBoardDocument;
import com.elite4.anandan.registrationservices.dto.*;
import com.elite4.anandan.registrationservices.model.EmployeeRole;
import com.elite4.anandan.registrationservices.model.Role;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.document.TransferRequestDocument;
import com.elite4.anandan.registrationservices.repository.RegistrationRepository;
import com.elite4.anandan.registrationservices.repository.RoleRepository;
import com.elite4.anandan.registrationservices.repository.RoomsOrHouseRepository;
import com.elite4.anandan.registrationservices.repository.TransferRequestRepository;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RegistrationService {
    private final RegistrationRepository registrationRepository;
    private final NotificationClient notificationClient;
    private final UserRepository userRepository;
    private final RoomsOrHouseRepository roomsOrHouseRepository;
    private final FileStorageService fileStorageService;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PhoneService phoneService;
    private final TransferRequestRepository transferRequestRepository;

    @Value("${tenant.default-password:Tenant@123}")
    private String defaultTenantPassword;

    public RegistrationWithRoomRequest create(Registration dto, RoomForRegistration room) {
        return create(dto, room, false);
    }

    public RegistrationWithRoomRequest create(Registration dto, RoomForRegistration room,
                                               boolean skipPendingTransferCheck) {
        // Validate that coliveUserName is provided (required field)
        if (dto.getColiveUserName() == null || dto.getColiveUserName().trim().isEmpty()) {
            throw new IllegalArgumentException("Client username must be provided for registration");
        }

        // Validate that contactNo is provided (required field)
        if (dto.getContactNo() == null || dto.getContactNo().trim().isEmpty()) {
            throw new IllegalArgumentException("Contact number must be provided for registration");
        }

        // Check if user with same contact number is already onboarded (only active/occupied registrations)
        Optional<RegistrationDocument> existingRegistrationByContact = registrationRepository.findByContactNoAndOccupied(dto.getContactNo(), Registration.roomOccupied.OCCUPIED);
        if (existingRegistrationByContact.isPresent()) {
            throw new IllegalArgumentException(
                    "A user is already onboarded with contact number '" + dto.getContactNo() +
                            "'. User name: '" + existingRegistrationByContact.get().getFname() +
                            "', Client: '" + existingRegistrationByContact.get().getColiveName() + "'"
            );
        }

        // Block registration if any registration with this contact number has a pending transfer
        if (!skipPendingTransferCheck) {
            List<RegistrationDocument> allByContact = registrationRepository.findAllByContactNo(dto.getContactNo());
            for (RegistrationDocument reg : allByContact) {
                List<TransferRequestDocument> transfers = transferRequestRepository
                        .findByTenantRegistrationId(reg.getId());
                boolean hasPending = transfers.stream()
                        .anyMatch(t -> t.getStatus() == TransferRequestDocument.TransferStatus.PENDING_SOURCE_APPROVAL
                                || t.getStatus() == TransferRequestDocument.TransferStatus.PENDING_DESTINATION_APPROVAL);
                if (hasPending) {
                    throw new IllegalArgumentException(
                            "Cannot register: a transfer request is pending for contact number '" + dto.getContactNo()
                                    + "'. Please complete or reject the transfer first.");
                }
            }
        }

        // Validate that fname, coliveName, and contactNo combination doesn't already exist
        List<RegistrationDocument> existingRegistrations = registrationRepository
                .findByFnameAndColiveNameAndContactNo(dto.getFname(), dto.getColiveName(), dto.getContactNo());
        if (!existingRegistrations.isEmpty()) {
            throw new IllegalArgumentException(
                    "Registration already exists with fname '" + dto.getFname() +
                            "', coliveName '" + dto.getColiveName() +
                            "', and contactNo '" + dto.getContactNo() + "'"
            );
        }
        String role = "";
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            Authentication authenticationnew = SecurityContextHolder.getContext().getAuthentication();
            //get role from authenticationnew
            role = authenticationnew.getAuthorities().stream()
                    .map(grantedAuthority -> grantedAuthority.getAuthority())
                    .filter(auth -> auth.startsWith("ROLE_"))
                    .findFirst()
                    .orElse("ROLE_USER");
        }
        String id = null;
        if (!role.equals("ROLE_USER")) {
            // Validate that only one of room number or house number is provided
            if ((room.getRoomNumber() != null && !room.getRoomNumber().trim().isEmpty()) &&
                    (room.getHouseNumber() != null && !room.getHouseNumber().trim().isEmpty())) {
                throw new IllegalArgumentException("Both room number and house number cannot be provided together");
            } else if ((room.getRoomNumber() == null || room.getRoomNumber().trim().isEmpty()) &&
                    (room.getHouseNumber() == null || room.getHouseNumber().trim().isEmpty())) {
                throw new IllegalArgumentException("Either room number or house number must be provided");
            }

            // Validate that coliveUserName exists in UserRepository
            Optional<User> clientUser = userRepository.findByUsername(dto.getColiveUserName());
            if (clientUser.isEmpty()) {
                throw new IllegalArgumentException(
                        "Client username '" + dto.getColiveUserName() + "' does not exist in the system. Please create a user with this client name first."
                );
            }

            // Validate that the provided room number/house number exists for this client
            User user = clientUser.get();
            Set<ClientAndRoomOnBoardId> clientAndRoomOnBoardIds = user.getClientDetails();

            if (clientAndRoomOnBoardIds == null || clientAndRoomOnBoardIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "Client username '" + dto.getColiveUserName() + "' does not have any rooms assigned"
                );
            }

            boolean roomFound = false;
            StringBuilder availableRooms = new StringBuilder();
            for (ClientAndRoomOnBoardId clientDetail : clientAndRoomOnBoardIds) {
                if (clientDetail.getColiveName().equals(dto.getColiveName())) {
                    if (clientDetail.getRoomOnBoardId() == null) {
                        continue;
                    }

                    Optional<RoomOnBoardDocument> roomOnBoardDocument = roomsOrHouseRepository.findById(clientDetail.getRoomOnBoardId());

                    if (roomOnBoardDocument.isPresent()) {
                        Set<Room> rooms = roomOnBoardDocument.get().getRooms();

                        // Debug logging to check if rooms are loaded
                        log.info("Found RoomOnBoardDocument with ID: {}, rooms count: {}",
                                clientDetail.getRoomOnBoardId(),
                                rooms != null ? rooms.size() : 0);

                        if (rooms == null || rooms.isEmpty()) {
                            log.warn("RoomOnBoardDocument {} has no rooms assigned", clientDetail.getRoomOnBoardId());
                            continue;
                        }

                        for (Room availableRoom : rooms) {
                            // Check if the provided room number matches
                            if (room.getRoomNumber() != null &&
                                    !room.getRoomNumber().isBlank() &&
                                    availableRoom.getRoomNumber() != null &&
                                    availableRoom.getRoomNumber().equals(room.getRoomNumber().trim().toString())) {
                                availableRooms.append(availableRoom.getRoomNumber()).append(" ");
                                log.info("Provided room number '{}' exists with client '{}' - proceeding with registration",
                                        room.getRoomNumber(), dto.getColiveUserName());
                                roomFound = true;
                                id = "R" + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
                                availableRoom.setOccupied(Room.roomOccupied.OCCUPIED);
                                rooms.add(availableRoom);
                                break;
                            }
                            // Check if the provided house number matches
                            else if (room.getHouseNumber() != null &&
                                    !room.getHouseNumber().isBlank() &&
                                    availableRoom.getHouseNumber() != null &&
                                    availableRoom.getHouseNumber().equals(room.getHouseNumber())) {
                                availableRooms.append(availableRoom.getHouseNumber()).append(" ");
                                log.info("Provided house number '{}' exists with client '{}' - proceeding with registration",
                                        room.getHouseNumber(), dto.getColiveUserName());
                                roomFound = true;
                                id = "H" + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
                                availableRoom.setOccupied(Room.roomOccupied.OCCUPIED);
                                rooms.add(availableRoom);
                                break;
                            }
                        }

                        if (roomFound) {
                            roomOnBoardDocument.get().setRooms(rooms);
                            roomsOrHouseRepository.save(roomOnBoardDocument.get());
                            break;
                        }
                    }
                }
            }

            if (!roomFound) {
                String roomIdentifier = room.getRoomNumber() != null ? room.getRoomNumber() : room.getHouseNumber();
                throw new IllegalArgumentException(
                        "Room/House number '" + roomIdentifier + "' is not associated with client '" + dto.getColiveUserName() +
                                "'. Available rooms: " + (availableRooms.length() > 0 ? availableRooms.toString().trim() : "None")
                );
            }
        }

        // Proceed with registration
        RegistrationDocument doc = toDocumentWithId(id, dto, room);
        doc = registrationRepository.save(doc);
        ensureTenantUserAccount(doc);

        // send notification after successful registration
        //sendRegistrationNotifications(dto, room);

        return toDto(doc);
    }

    /**
     * Creates a registration with file uploads (aadhar photo and document upload)
     * COMPLETE IMPLEMENTATION with file saving:
     * - generateFileName() to create unique filenames
     * - uploadFile() to upload to LOCAL/S3/AZURE storage
     * - Save file paths to MongoDB database
     *
     * @param dto                 Registration DTO
     * @param room                Room DTO
     * @param aadharPhotoBytes    Aadhar photo as byte array (optional)
     * @param documentUploadBytes Document upload as byte array (optional)
     * @return RegistrationWithRoomRequest with file paths saved to DB
     */
    public RegistrationWithRoomRequest createWithFiles(Registration dto, RoomForRegistration room, byte[] aadharPhotoBytes, byte[] documentUploadBytes) {
        // Step 1: Create registration (get the ID)
        RegistrationWithRoomRequest result = create(dto, room);
        String registrationId = result.getId();

        log.info("Starting file upload for new registration: {}", registrationId);

        // Step 2: Upload aadhar photo if provided
        if (aadharPhotoBytes != null && aadharPhotoBytes.length > 0) {
            try {
                // generateFileName() - Create unique filename with timestamp
                String fileName = fileStorageService.generateFileName(registrationId, "aadhar.jpg", "aadhar");
                log.info("Generated aadhar filename: {}", fileName);

                // uploadFile() - Save to storage (LOCAL/S3/AZURE)
                String aadharPath = fileStorageService.uploadFile(fileName, aadharPhotoBytes, "image/jpeg");
                log.info("Aadhar uploaded to: {}", aadharPath);

                // Store path in DTO
                dto.setAadharPhotoPath(aadharPath);
            } catch (Exception e) {
                log.error("Aadhar upload failed for {}: {}", registrationId, e.getMessage(), e);
            }
        }

        // Step 3: Upload document if provided
        if (documentUploadBytes != null && documentUploadBytes.length > 0) {
            try {
                // generateFileName() - Create unique filename with timestamp
                String fileName = fileStorageService.generateFileName(registrationId, "document.pdf", "document");
                log.info("Generated document filename: {}", fileName);

                // uploadFile() - Save to storage (LOCAL/S3/AZURE)
                String documentPath = fileStorageService.uploadFile(fileName, documentUploadBytes, "application/pdf");
                log.info("Document uploaded to: {}", documentPath);

                // Store path in DTO
                dto.setDocumentUploadPath(documentPath);
            } catch (Exception e) {
                log.error("Document upload failed for {}: {}", registrationId, e.getMessage(), e);
            }
        }

        // Step 4: PERSIST file paths to MongoDB
        if ((dto.getAadharPhotoPath() != null && !dto.getAadharPhotoPath().isBlank()) ||
                (dto.getDocumentUploadPath() != null && !dto.getDocumentUploadPath().isBlank())) {
            try {
                Optional<RegistrationDocument> existingDoc = registrationRepository.findById(registrationId);
                if (existingDoc.isPresent()) {
                    RegistrationDocument doc = existingDoc.get();

                    // Set aadhar photo path
                    if (dto.getAadharPhotoPath() != null && !dto.getAadharPhotoPath().isBlank()) {
                        doc.setAadharPhotoPath(dto.getAadharPhotoPath());
                        log.info("Aadhar path saved to MongoDB: {}", registrationId);
                    }

                    // Set document path
                    if (dto.getDocumentUploadPath() != null && !dto.getDocumentUploadPath().isBlank()) {
                        doc.setDocumentUploadPath(dto.getDocumentUploadPath());
                        log.info("Document path saved to MongoDB: {}", registrationId);
                    }

                    // SAVE to MongoDB
                    registrationRepository.save(doc);
                    ensureTenantUserAccount(doc);
                    log.info("File paths persisted for registration: {}", registrationId);

                    // Update response with persisted data
                    result.setRegistration(toRegistration(doc));
                }
            } catch (Exception e) {
                log.error("Failed to persist file paths to DB for {}: {}", registrationId, e.getMessage(), e);
            }
        }

        log.info("File creation completed. Registration: {}, Aadhar saved: {}, Document saved: {}",
                registrationId,
                dto.getAadharPhotoPath() != null,
                dto.getDocumentUploadPath() != null);

        return result;
    }


    private void ensureTenantUserAccount(RegistrationDocument doc) {
        try {
            Optional<User> existingUser = findExistingTenantUser(doc);
            String roleId = getOrCreateUserRoleId();

            if (existingUser.isPresent()) {
                User user = existingUser.get();
                if (roleId == null || user.getRoleIds() == null || !user.getRoleIds().contains(roleId)) {
                    log.info("Existing user {} matched tenant onboarding but does not have ROLE_USER. Skipping user update.", user.getId());
                    return;
                }

                boolean changed = false;
                String latestEmail = blankToNull(doc.getEmail());
                String latestPhoneRaw = blankToNull(doc.getContactNo());
                String latestPhoneE164 = latestPhoneRaw != null ? phoneService.toE164(latestPhoneRaw) : null;
                String latestAadharPath = blankToNull(doc.getAadharPhotoPath());

                if (!Objects.equals(blankToNull(user.getEmail()), latestEmail)) {
                    user.setEmail(latestEmail);
                    changed = true;
                }
                if (!Objects.equals(blankToNull(user.getPhoneRaw()), latestPhoneRaw)) {
                    user.setPhoneRaw(latestPhoneRaw);
                    changed = true;
                }
                if (!Objects.equals(blankToNull(user.getPhoneE164()), latestPhoneE164)) {
                    user.setPhoneE164(latestPhoneE164);
                    changed = true;
                }
                if (!Objects.equals(blankToNull(user.getAadharPhotoPath()), latestAadharPath)) {
                    user.setAadharPhotoPath(latestAadharPath);
                    changed = true;
                }
                if (!user.isActive()) {
                    user.setActive(true);
                    changed = true;
                }
                if (changed) {
                    user.setUpdatedAt(Instant.now());
                    userRepository.save(user);
                    log.info("Existing tenant user updated with latest registration data: {}", user.getId());
                }
                return;
            }

            User tenantUser = new User();
            tenantUser.setUsername(buildTenantUsername(doc));
            tenantUser.setPasswordHash(passwordEncoder.encode(defaultTenantPassword));
            tenantUser.setEmail(blankToNull(doc.getEmail()));
            tenantUser.setPhoneE164(phoneService.toE164(doc.getContactNo()));
            tenantUser.setPhoneRaw(doc.getContactNo());
            tenantUser.setAadharPhotoPath(doc.getAadharPhotoPath());
            tenantUser.setActive(true);
            tenantUser.setForcePasswordChange(true);
            tenantUser.setCreatedAt(Instant.now());
            tenantUser.setUpdatedAt(Instant.now());
            if (roleId != null) {
                tenantUser.setRoleIds(new HashSet<>(Collections.singleton(roleId)));
            }
            userRepository.save(tenantUser);
            sendTenantCredentialsNotification(doc);
            log.info("Tenant user account auto-created for registration: {}", doc.getId());
        } catch (Exception e) {
            log.error("Failed to auto-create tenant user account for registration {}: {}", doc.getId(), e.getMessage(), e);
        }
    }

    private Optional<User> findExistingTenantUser(RegistrationDocument doc) {
        if (doc.getEmail() != null && !doc.getEmail().isBlank()) {
            Optional<User> userByEmail = userRepository.findByEmail(doc.getEmail().trim());
            if (userByEmail.isPresent()) {
                return userByEmail;
            }
        }
        if (doc.getContactNo() != null && !doc.getContactNo().isBlank()) {
            String e164 = phoneService.toE164(doc.getContactNo());
            if (e164 != null && !e164.isBlank()) {
                Optional<User> userByE164 = userRepository.findByPhoneE164(e164);
                if (userByE164.isPresent()) {
                    return userByE164;
                }
            }
            Optional<User> userByRaw = userRepository.findByPhoneRaw(doc.getContactNo().trim());
            if (userByRaw.isPresent()) {
                return userByRaw;
            }
        }
        return Optional.empty();
    }

    private String getOrCreateUserRoleId() {
        try {
            Optional<Role> existing = roleRepository.findByName(EmployeeRole.ROLE_USER);
            if (existing.isPresent()) {
                return existing.get().getId();
            }
        } catch (Exception e) {
            log.warn("ROLE_USER lookup by name failed, falling back to scan: {}", e.getMessage());
        }

        try {
            Optional<Role> scanned = roleRepository.findAll().stream()
                    .filter(Objects::nonNull)
                    .filter(role -> role.getName() == EmployeeRole.ROLE_USER)
                    .findFirst();
            if (scanned.isPresent()) {
                return scanned.get().getId();
            }
        } catch (Exception e) {
            log.warn("ROLE_USER scan failed before create: {}", e.getMessage());
        }

        try {
            Role role = new Role();
            role.setName(EmployeeRole.ROLE_USER);
            return roleRepository.save(role).getId();
        } catch (Exception e) {
            log.warn("ROLE_USER create failed, retrying scan: {}", e.getMessage());
            return roleRepository.findAll().stream()
                    .filter(Objects::nonNull)
                    .filter(role -> role.getName() == EmployeeRole.ROLE_USER)
                    .map(Role::getId)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Unable to resolve ROLE_USER id", e));
        }
    }

    private String buildTenantUsername(RegistrationDocument doc) {
        String base = (doc.getId() != null && !doc.getId().isBlank() ? doc.getId() : "tenant")
                .replaceAll("[^a-zA-Z0-9_-]", "_")
                .toLowerCase();
        String candidate = base;
        int counter = 1;
        while (userRepository.findByUsername(candidate).isPresent()) {
            candidate = base + "_" + counter++;
        }
        return candidate;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void sendTenantCredentialsNotification(RegistrationDocument doc) {
        String loginIdentifier = (doc.getEmail() != null && !doc.getEmail().isBlank()) ? doc.getEmail().trim() : doc.getContactNo();
        String message = "Your CoLive Connect tenant account is ready. Login with " + loginIdentifier +
                " and temporary password " + defaultTenantPassword +
                ". You will be asked to change your password after first login.";

        try {
            if (doc.getEmail() != null && !doc.getEmail().isBlank()) {
                notificationClient.sendEmail(doc.getEmail().trim(), "Your CoLive Connect login is ready", message);
            }
        } catch (Exception ex) {
            log.warn("Failed to send tenant onboarding email for registration {}: {}", doc.getId(), ex.getMessage());
        }

        try {
            if (doc.getContactNo() != null && !doc.getContactNo().isBlank()) {
                notificationClient.sendSms(doc.getContactNo().trim(), message);
            }
        } catch (Exception ex) {
            log.warn("Failed to send tenant onboarding SMS for registration {}: {}", doc.getId(), ex.getMessage());
        }
    }
    public Optional<RegistrationWithRoomRequest> update(String id, Registration dto, RoomForRegistration room, Boolean changingRoom) {
        return registrationRepository.findById(id)
                .map(existing -> {
                    RegistrationDocument doc = toDocument(dto, room);
                    doc.setId(id);
                    // Determine room assignment and whether to update ID
                    boolean isInitialRegistration = isInitialRegistrationId(id);
                    boolean shouldKeepExistingRoom = isInitialRegistration && existing.getRoomForRegistration() != null && !changingRoom;
                    if (shouldKeepExistingRoom) {
                        doc.setRoomForRegistration(existing.getRoomForRegistration());
                    } else {
                        doc.setRoomForRegistration(room);
                        // Generate new ID for update if not an initial registration or room is changing
                        doc.setId(generateUpdateId());
                    }
                    doc = registrationRepository.save(doc);
                    // Delete old record if changing room
                    if (changingRoom) {
                        registrationRepository.deleteById(id);
                    }
                    return toDto(doc);
                });
    }

    /**
     * Checks if the ID represents an initial registration (H- or R- prefix).
     *
     * @param id the registration ID to check
     * @return true if ID starts with H- or R-, false otherwise
     */
    private boolean isInitialRegistrationId(String id) {
        return id != null && (id.startsWith("H-") || id.startsWith("R-")|| id.startsWith("N-"));
    }

    /**
     * Generates a new update registration ID with U- prefix.
     *
     * @return new unique update ID
     */
    private String generateUpdateId() {
        return "U-" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }

    public List<RegistrationWithRoomRequest> findByColiveNameAndColiveUserName(String coliveUserName, String coliveName) {
        return registrationRepository.findByColiveNameAndColiveUserName(coliveName, coliveUserName).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Optional<RegistrationWithRoomRequest> findById(String id) {
        return registrationRepository.findById(id).map(this::toDto);
    }

    public Optional<RegistrationWithRoomRequest> findByEmail(String email) {
        return registrationRepository.findByEmail(email).map(this::toDto);
    }

    public Optional<RegistrationWithRoomRequest> findByContactNo(String contactNo) {
        Optional<RegistrationDocument> reg = registrationRepository.findByContactNo(String.valueOf(contactNo).trim());
        if (reg.isPresent()) {
            return reg.map(this::toDto);
        } else {
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
            Phonenumber.PhoneNumber number = null;
            try {
                number = phoneUtil.parse(contactNo, "IN");
            } catch (NumberParseException e) {
                throw new RuntimeException(e);
            }
            long nationalNumber = number.getNationalNumber();
            Optional<RegistrationDocument> regWithNumber = registrationRepository.findByContactNo(String.valueOf(contactNo));
            if (regWithNumber.isPresent()) {
                return regWithNumber.map(this::toDto);
            } else {
                String newNumber = "0" + String.valueOf(nationalNumber);
                return registrationRepository.findByContactNo(newNumber).map(this::toDto);
            }
        }
    }

    public List<RegistrationWithRoomRequest> findAll() {
        return registrationRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findAllByRoomNumber(String coliveUserName, String coliveName, String roomNumber) {
        return registrationRepository.findByColiveNameAndColiveUserNameAndRoomForRegistrationRoomNumberAndOccupied(
                        coliveName,
                        coliveUserName,
                        roomNumber,
                        Registration.roomOccupied.OCCUPIED).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findAllByHouseNumber(String coliveUserName, String coliveName, String roomNumber) {
        return registrationRepository.findByColiveUserNameAndColiveNameAndRoomForRegistrationHouseNumberAndOccupied(coliveUserName, coliveName, roomNumber, Registration.roomOccupied.OCCUPIED).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findAllByRoomType(String coliveUserName, String coliveName, String roomType) {
        return registrationRepository.findAllByColiveUserNameAndColiveNameAndRoomForRegistrationRoomType(coliveUserName, coliveName, roomType).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findAllByHouseType(String coliveUserName, String coliveName, String houseType) {
        return registrationRepository.findAllByColiveUserNameAndColiveNameAndRoomForRegistrationHouseType(coliveUserName, coliveName, houseType).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findAllByGender(Registration.Gender gender) {
        return registrationRepository.findAllByGender(gender).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findByFname(String fname) {
        return registrationRepository.findByfname(fname).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findByLname(String lname) {
        return registrationRepository.findBylname(lname).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findByMname(String mname) {
        return registrationRepository.findBymname(mname).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findByAddress(String address) {
        return registrationRepository.findByaddress(address).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findByPincode(String pincode) {
        return registrationRepository.findBypincode(pincode).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findByCheckInDate(String checkInDate) {
        return registrationRepository.findBycheckInDate(checkInDate).stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RegistrationWithRoomRequest> findByCheckOutDate(String checkOutDate) {
        return registrationRepository.findBycheckOutDate(checkOutDate).stream().map(this::toDto).collect(Collectors.toList());
    }

    public ResponseEntity<String> checkoutAll(Set<UpdateUserForCheckOut> userForCheckOutForAll) {
        List<String> vacatedRegistrationIds = new ArrayList<>();
        List<RegistrationWithRoomRequest> result = new ArrayList<>();
        for (UpdateUserForCheckOut updateUserForCheckOut : userForCheckOutForAll.stream().toList()) {
            Optional<RegistrationWithRoomRequest> updated = updateCheckOutDateByID(updateUserForCheckOut.getRegistrationId(), updateUserForCheckOut.getCheckOutDate());
            updated.ifPresent(result::add);
            vacatedRegistrationIds.add(updateUserForCheckOut.getRegistrationId());
        }
        return ResponseEntity.ok("Registrations with IDs " + vacatedRegistrationIds + " have been checked out and rooms updated accordingly.");
    }

    public Optional<RegistrationWithRoomRequest> checkout(UpdateUserForCheckOut updateUserForCheckOut) {
        return checkout(updateUserForCheckOut, false);
    }

    /**
     * Checkout a tenant with an option to skip the pending transfer check.
     * Use skipTransferCheck=true only for internal transfer approval flow.
     */
    public Optional<RegistrationWithRoomRequest> checkout(UpdateUserForCheckOut updateUserForCheckOut,
                                                           boolean skipTransferCheck) {
        if (updateUserForCheckOut == null || updateUserForCheckOut.getRegistrationId() == null) {
            return Optional.empty();
        }

        String id = updateUserForCheckOut.getRegistrationId();
        Date checkOutDate = updateUserForCheckOut.getCheckOutDate();

        // Block checkout if a transfer request is pending for this tenant
        if (!skipTransferCheck) {
            List<TransferRequestDocument> pendingTransfers = transferRequestRepository
                    .findByTenantRegistrationId(id);
            boolean hasActiveTransfer = pendingTransfers.stream()
                    .anyMatch(t -> t.getStatus() == TransferRequestDocument.TransferStatus.PENDING_SOURCE_APPROVAL
                            || t.getStatus() == TransferRequestDocument.TransferStatus.PENDING_DESTINATION_APPROVAL);
            if (hasActiveTransfer) {
                throw new IllegalArgumentException(
                        "Cannot checkout tenant: a transfer request is pending. Please complete or reject the transfer first.");
            }
        }

        return registrationRepository.findById(id)
                .map(doc -> {
                    // Mark registration as vacated
                    doc.setCheckOutDate(checkOutDate);
                    doc.setOccupied(Registration.roomOccupied.VACATED);
                    doc.setActive(Boolean.FALSE);
                    RegistrationDocument saved = registrationRepository.save(doc);

                    // Update room occupancy status
                    updateRoomOccupancyAfterCheckout(doc);

                    return toDto(saved);
                });
    }

    /**
     * Updates the room occupancy status after a guest checks out.
     *
     * @param registrationDoc the registration document that was checked out
     */
    private void updateRoomOccupancyAfterCheckout(RegistrationDocument registrationDoc) {
        List<User> clientUsers = userRepository.findAllByclientDetailsColiveName(registrationDoc.getColiveName());

        if (clientUsers.isEmpty()) {
            return;
        }

        User user = clientUsers.get(0);
        Set<ClientAndRoomOnBoardId> clientDetails = user.getClientDetails();

        if (clientDetails == null || clientDetails.isEmpty()) {
            return;
        }

        for (ClientAndRoomOnBoardId clientDetail : clientDetails) {
            if (!clientDetail.getColiveName().equals(registrationDoc.getColiveName())) {
                continue;
            }

            updateRoomStatusInRoomOnBoard(clientDetail, registrationDoc);
        }
    }

    /**
     * Updates the occupancy status of a specific room in the RoomOnBoardDocument.
     *
     * @param clientDetail    the client and room details
     * @param registrationDoc the registration document being checked out
     */
    private void updateRoomStatusInRoomOnBoard(ClientAndRoomOnBoardId clientDetail, RegistrationDocument registrationDoc) {
        Optional<RoomOnBoardDocument> roomOnBoardOpt = roomsOrHouseRepository.findById(clientDetail.getRoomOnBoardId());

        if (roomOnBoardOpt.isEmpty()) {
            return;
        }

        RoomOnBoardDocument roomOnBoard = roomOnBoardOpt.get();
        Set<Room> rooms = roomOnBoard.getRooms();

        if (rooms == null || rooms.isEmpty()) {
            return;
        }

        boolean roomUpdated = false;
        String coliveUserName = registrationDoc.getColiveUserName();
        RoomForRegistration checkedOutRoom = registrationDoc.getRoomForRegistration();

        for (Room room : rooms) {
            if (!isRoomMatch(room, checkedOutRoom)) {
                continue;
            }

            // Count remaining occupied registrations for this room
            int occupiedCount = countOccupiedRegistrations(coliveUserName, clientDetail.getClientCategory(), room);

            // Update room occupancy status
            if (occupiedCount > 0) {
                room.setOccupied(Room.roomOccupied.PARTIALLY_OCCUPIED);
            } else {
                room.setOccupied(Room.roomOccupied.NOT_OCCUPIED);
            }

            roomUpdated = true;
            break;
        }

        if (roomUpdated) {
            roomOnBoard.setRooms(rooms);
            roomsOrHouseRepository.save(roomOnBoard);
        }
    }

    /**
     * Checks if a room matches the checked-out room (by room number or house number).
     *
     * @param room           the room to check
     * @param checkedOutRoom the room that was checked out
     * @return true if rooms match, false otherwise
     */
    private boolean isRoomMatch(Room room, RoomForRegistration checkedOutRoom) {
        if (room == null || checkedOutRoom == null) {
            return false;
        }

        return (room.getRoomNumber() != null && room.getRoomNumber().equals(checkedOutRoom.getRoomNumber())) ||
                (room.getHouseNumber() != null && room.getHouseNumber().equals(checkedOutRoom.getHouseNumber()));
    }

    /**
     * Counts occupied registrations for a specific room based on client category.
     *
     * @param coliveUserName the client user name
     * @param clientCategory the client category (PG, HOSTEL, etc.)
     * @param room           the room to check
     * @return count of occupied registrations
     */
    private int countOccupiedRegistrations(String coliveUserName, String clientCategory, Room room) {
        List<RegistrationDocument> registrations;

        if (clientCategory != null && (clientCategory.equals("PG") || clientCategory.equals("HOSTEL"))) {
            registrations = registrationRepository.findByColiveNameAndColiveUserNameAndRoomForRegistrationRoomNumberAndOccupied(
                    coliveUserName, coliveUserName, room.getRoomNumber(), Registration.roomOccupied.OCCUPIED);
        } else {
            registrations = registrationRepository.findByColiveUserNameAndColiveNameAndRoomForRegistrationHouseNumberAndOccupied(
                    coliveUserName, coliveUserName, room.getHouseNumber(), Registration.roomOccupied.OCCUPIED);
        }

        return registrations.size();
    }

    /**
     * Updates the checkout date for the first registration matching the given first name and room number.
     *
     * @param checkOutDate new checkout date to set
     * @return the updated registration if found, empty otherwise
     */
    public Optional<RegistrationWithRoomRequest> updateCheckOutDateByID(String id, Date checkOutDate) {
        return checkout(new UpdateUserForCheckOut(id, checkOutDate));
    }

    /**
     * Returns all registrations (with room info) where occupied status is NOT_OCCUPIED.
     */
    public List<RegistrationWithRoomRequest> findRoomsWithNotOccupiedStatus() {
        return registrationRepository.findByOccupied(Registration.roomOccupied.NOT_OCCUPIED)
                .stream()
                .map(doc -> toDto(doc))
                .collect(Collectors.toList());
    }

    /**
     * Returns all registrations (with room info) where occupied status is VACATED.
     * Optionally filtered by coliveUserName and coliveName.
     */
    public List<RegistrationWithRoomRequest> findRoomsWithVacatedaStatus(String coliveUserName, String coliveName) {
        List<RegistrationDocument> vacatedList;
        if (coliveUserName != null && !coliveUserName.isBlank() && coliveName != null && !coliveName.isBlank()) {
            vacatedList = registrationRepository.findByOccupiedAndColiveUserNameAndColiveName(
                    Registration.roomOccupied.VACATED, coliveUserName, coliveName);
        } else if (coliveUserName != null && !coliveUserName.isBlank()) {
            vacatedList = registrationRepository.findByOccupiedAndColiveUserName(
                    Registration.roomOccupied.VACATED, coliveUserName);
        } else {
            vacatedList = registrationRepository.findByOccupied(Registration.roomOccupied.VACATED);
        }
        return vacatedList.stream()
                .filter(doc -> doc.getCheckOutDate() != null && !doc.getCheckOutDate().toString().isBlank()&& doc.getActive()!= null && !doc.getActive())
                .map(doc -> toDto(doc)).limit(500)
                .collect(Collectors.toList());
    }

    /**
     * Returns all registrations (with room info) where occupied status is OCCUPIED.
     */
    public List<RegistrationWithRoomRequest> findRoomsWithOccupiedStatus() {
        return registrationRepository.findByOccupied(Registration.roomOccupied.OCCUPIED)
                .stream()
                .map(doc -> toDto(doc))
                .collect(Collectors.toList());
    }

    public boolean existsByEmail(String email) {
        return registrationRepository.findByEmail(email).isPresent();
    }

    public boolean existsByContactNo(String contactNo) {
        return registrationRepository.findByContactNo(contactNo).isPresent();
    }

    public void deleteById(String id) {
        registrationRepository.deleteById(id);
    }


    public boolean existsById(String id) {
        return registrationRepository.existsById(id);
    }

    private RegistrationDocument toDocumentWithId(String id, Registration dto, RoomForRegistration room) {
        RegistrationDocument registrationDocument = toDocument(dto, room);
        if(id==null){
            id = "N" + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        }
        registrationDocument.setId(id);
        registrationDocument.setOccupied(Registration.roomOccupied.OCCUPIED);
        return registrationDocument;
    }

    private RegistrationDocument toDocument(Registration dto, RoomForRegistration room) {
        return RegistrationDocument.builder()
                .fname(dto.getFname())
                .lname(dto.getLname())
                .email(dto.getEmail())
                .mname(dto.getMname())
                .contactNo(dto.getContactNo())
                .gender(dto.getGender())
                .address(dto.getAddress())
                .pincode(dto.getPincode())
                .aadharPhotoPath(dto.getAadharPhotoPath())
                .documentUploadPath(dto.getDocumentUploadPath())
                .documentType(dto.getDocumentType())
                .documentNumber(dto.getDocumentNumber())
                .checkInDate(dto.getCheckInDate())
                .checkOutDate(dto.getCheckOutDate())
                .occupied(dto.getRoomOccupied())
                .coliveUserName(dto.getColiveUserName())
                .coliveName(dto.getColiveName())
                .advanceAmount(dto.getAdvanceAmount())
                .roomRent(dto.getRoomRent())
                .roomForRegistration(room)
                .parentContactNo(dto.getParentContactNo())
                .parentName(dto.getParentName())
                .build();
    }

    private Registration toRegistration(RegistrationDocument doc) {
        Registration dto = new Registration();
        dto.setFname(doc.getFname());
        dto.setLname(doc.getLname());
        dto.setEmail(doc.getEmail());
        dto.setMname(doc.getMname());
        dto.setContactNo(doc.getContactNo());
        dto.setGender(doc.getGender());
        dto.setAddress(doc.getAddress());
        dto.setPincode(doc.getPincode());
        dto.setAadharPhotoPath(doc.getAadharPhotoPath());
        dto.setDocumentUploadPath(doc.getDocumentUploadPath());
        dto.setDocumentType(doc.getDocumentType());
        dto.setDocumentNumber(doc.getDocumentNumber());
        dto.setCheckInDate(doc.getCheckInDate());
        dto.setCheckOutDate(doc.getCheckOutDate());
        dto.setAdvanceAmount(doc.getAdvanceAmount());
        dto.setRoomOccupied(doc.getOccupied());
        dto.setColiveUserName(doc.getColiveUserName());
        dto.setColiveName(doc.getColiveName());
        dto.setRoomRent(doc.getRoomRent());
        dto.setParentContactNo(doc.getParentContactNo());
        dto.setParentName(doc.getParentName());
        dto.setRegId(doc.getId());
        return dto;
    }

    private RegistrationWithRoomRequest toDto(RegistrationDocument doc) {
        RegistrationWithRoomRequest dtoWithRoom = new RegistrationWithRoomRequest();
        dtoWithRoom.setRegistration(toRegistration(doc));
        dtoWithRoom.setId(doc.getId());
        if (doc.getRoomForRegistration() != null) {
            RoomForRegistration room = new RoomForRegistration();
            room.setRoomNumber(doc.getRoomForRegistration().getRoomNumber());
            room.setHouseNumber(doc.getRoomForRegistration().getHouseNumber());
            room.setRoomType(doc.getRoomForRegistration().getRoomType());
            room.setRoomCapacity(doc.getRoomForRegistration().getRoomCapacity());
            room.setHouseType(doc.getRoomForRegistration().getHouseType());
            dtoWithRoom.setRoom(room);
        }
        return dtoWithRoom;
    }

    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    private void sendRegistrationNotifications(Registration dto, Room room) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("fname", dto.getFname());
        variables.put("roomNumber", room.getRoomNumber());
        variables.put("houseNumber", room.getHouseNumber());
        variables.put("checkInDate", dto.getCheckInDate());
        if (dto.getEmail() != null) {
            notificationClient.sendEmailWithTemplate(dto.getEmail(), "Registration Successful", "registration-success", variables);
        }
        if (dto.getContactNo() != null) {
            notificationClient.sendSms(dto.getContactNo(), "Hello " + dto.getFname() + ", your registration has been completed.");
        }
    }

    @Recover
    private void recoverSendRegistrationNotifications(Exception e, Registration dto, Room room) {
        // Log the failure and potentially store for later retry or manual processing
        System.err.println("Failed to send registration notifications for " + dto.getFname() + " after 3 attempts: " + e.getMessage());
        // Could implement additional recovery logic here, like saving to a retry queue
    }

    /**
     * Updates registration with full file management capabilities
     * - Deletes old files if they exist
     * - Uploads new files
     * - Updates file paths in database
     *
     * @param id                  Registration ID
     * @param dto                 Registration DTO with new data
     * @param room                Room DTO
     * @param changingRoom        Whether room is being changed
     * @param newAadharPhotoBytes New aadhar photo (optional)
     * @param newDocumentBytes    New document (optional)
     * @return Updated registration
     */
    public Optional<RegistrationWithRoomRequest> updateWithFiles(String id, Registration dto, RoomForRegistration room,
                                                                 Boolean changingRoom,
                                                                 byte[] newAadharPhotoBytes,
                                                                 byte[] newDocumentBytes) {
        return registrationRepository.findById(id)
                .map(existing -> {
                    // Handle aadhar photo: delete old if exists, upload new if provided
                    if (existing.getAadharPhotoPath() != null && !existing.getAadharPhotoPath().isBlank()) {
                        if (newAadharPhotoBytes != null) {
                            try {
                                if (fileStorageService.fileExists(existing.getAadharPhotoPath())) {
                                    fileStorageService.deleteFile(existing.getAadharPhotoPath());
                                    log.info("Old aadhar photo deleted for registration: {}", id);
                                }
                            } catch (Exception e) {
                                log.warn("Failed to delete old aadhar photo for registration: {}", id, e);
                            }
                        }
                    }

                    // Upload new aadhar photo
                    if (newAadharPhotoBytes != null && newAadharPhotoBytes.length > 0) {
                        try {
                            String fileName = fileStorageService.generateFileName(id, "aadhar.jpg", "aadhar");
                            String newPath = fileStorageService.uploadFile(fileName, newAadharPhotoBytes, "image/jpeg");
                            dto.setAadharPhotoPath(newPath);
                            log.info("New aadhar photo uploaded for registration: {} at path: {}", id, newPath);
                        } catch (Exception e) {
                            log.error("Failed to upload new aadhar photo for registration: {}", id, e);
                            dto.setAadharPhotoPath(existing.getAadharPhotoPath());
                        }
                    } else {
                        dto.setAadharPhotoPath(existing.getAadharPhotoPath());
                    }

                    // Handle document: delete old if exists, upload new if provided
                    if (existing.getDocumentUploadPath() != null && !existing.getDocumentUploadPath().isBlank()) {
                        if (newDocumentBytes != null) {
                            try {
                                if (fileStorageService.fileExists(existing.getDocumentUploadPath())) {
                                    fileStorageService.deleteFile(existing.getDocumentUploadPath());
                                    log.info("Old document deleted for registration: {}", id);
                                }
                            } catch (Exception e) {
                                log.warn("Failed to delete old document for registration: {}", id, e);
                            }
                        }
                    }

                    // Upload new document
                    if (newDocumentBytes != null && newDocumentBytes.length > 0) {
                        try {
                            String fileName = fileStorageService.generateFileName(id, "document.pdf", "document");
                            String newPath = fileStorageService.uploadFile(fileName, newDocumentBytes, "application/pdf");
                            dto.setDocumentUploadPath(newPath);
                            log.info("New document uploaded for registration: {} at path: {}", id, newPath);
                        } catch (Exception e) {
                            log.error("Failed to upload new document for registration: {}", id, e);
                            dto.setDocumentUploadPath(existing.getDocumentUploadPath());
                        }
                    } else {
                        dto.setDocumentUploadPath(existing.getDocumentUploadPath());
                    }

                    RegistrationDocument doc = toDocument(dto, room);
                    doc.setId(id);

                    boolean isInitialRegistration = isInitialRegistrationId(id);
                    boolean shouldKeepExistingRoom = isInitialRegistration && existing.getRoomForRegistration() != null && !changingRoom;

                    if (shouldKeepExistingRoom) {
                        doc.setRoomForRegistration(existing.getRoomForRegistration());
                    } else {
                        doc.setRoomForRegistration(room);
                        doc.setId(generateUpdateId());
                    }

                    doc = registrationRepository.save(doc);

                    if (changingRoom) {
                        registrationRepository.deleteById(id);
                    }

                    return toDto(doc);
                });
    }

    /**
     * Downloads a file for a registration using FileStorageService.downloadFile()
     * Retrieves file content by path
     *
     * @param registrationId Registration ID
     * @param fileType       Type of file ("aadhar" or "document")
     * @return File content as byte array
     */
    public byte[] downloadFile(String registrationId, String fileType) {
        return registrationRepository.findById(registrationId)
                .map(doc -> {
                    String filePath = "aadhar".equalsIgnoreCase(fileType)
                            ? doc.getAadharPhotoPath()
                            : doc.getDocumentUploadPath();

                    if (filePath == null || filePath.isBlank()) {
                        throw new IllegalArgumentException("No " + fileType + " file found for registration: " + registrationId);
                    }

                    try {
                        byte[] content = fileStorageService.downloadFile(filePath);
                        log.info("File downloaded for registration: {}, type: {}, size: {} bytes", registrationId, fileType, content.length);
                        return content;
                    } catch (Exception e) {
                        log.error("Failed to download {} for registration: {}", fileType, registrationId, e);
                        throw new RuntimeException("Failed to download file: " + e.getMessage(), e);
                    }
                })
                .orElseThrow(() -> new IllegalArgumentException("Registration not found: " + registrationId));
    }

    /**
     * Checks if file exists for a registration using FileStorageService.fileExists()
     *
     * @param registrationId Registration ID
     * @param fileType       Type of file ("aadhar" or "document")
     * @return true if file exists, false otherwise
     */
    public boolean fileExists(String registrationId, String fileType) {
        return registrationRepository.findById(registrationId)
                .map(doc -> {
                    String filePath = "aadhar".equalsIgnoreCase(fileType)
                            ? doc.getAadharPhotoPath()
                            : doc.getDocumentUploadPath();

                    if (filePath == null || filePath.isBlank()) {
                        return false;
                    }

                    try {
                        boolean exists = fileStorageService.fileExists(filePath);
                        log.info("File exists check for registration: {}, type: {}, result: {}", registrationId, fileType, exists);
                        return exists;
                    } catch (Exception e) {
                        log.warn("Error checking file existence for registration: {}, type: {}", registrationId, fileType, e);
                        return false;
                    }
                })
                .orElse(false);
    }

    /**
     * Deletes files for a registration using FileStorageService.deleteFile()
     * Deletes both aadhar photo and document if they exist
     * Called when member/tenant is removed
     *
     * @param registrationId Registration ID
     */
    public void deleteRegistrationFiles(String registrationId) {
        registrationRepository.findById(registrationId)
                .ifPresent(doc -> {
                    // Delete aadhar photo
                    if (doc.getAadharPhotoPath() != null && !doc.getAadharPhotoPath().isBlank()) {
                        try {
                            if (fileStorageService.fileExists(doc.getAadharPhotoPath())) {
                                fileStorageService.deleteFile(doc.getAadharPhotoPath());
                                log.info("Aadhar photo deleted for registration: {}", registrationId);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to delete aadhar photo for registration: {}", registrationId, e);
                        }
                    }

                    // Delete document
                    if (doc.getDocumentUploadPath() != null && !doc.getDocumentUploadPath().isBlank()) {
                        try {
                            if (fileStorageService.fileExists(doc.getDocumentUploadPath())) {
                                fileStorageService.deleteFile(doc.getDocumentUploadPath());
                                log.info("Document deleted for registration: {}", registrationId);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to delete document for registration: {}", registrationId, e);
                        }
                    }
                });
    }

    /**
     * Replaces a single file using FileStorageService methods
     * Handles deletion of old file and upload of new one
     *
     * @param registrationId Registration ID
     * @param fileType       Type of file to replace ("aadhar" or "document")
     * @param newFileBytes   New file content
     * @param contentType    MIME type of file
     * @return New file path
     */
    public String replaceRegistrationFile(String registrationId, String fileType, byte[] newFileBytes, String contentType) {
        return registrationRepository.findById(registrationId)
                .map(doc -> {
                    String oldPath = "aadhar".equalsIgnoreCase(fileType)
                            ? doc.getAadharPhotoPath()
                            : doc.getDocumentUploadPath();

                    // Delete old file using fileStorageService.deleteFile()
                    if (oldPath != null && !oldPath.isBlank()) {
                        try {
                            if (fileStorageService.fileExists(oldPath)) {
                                fileStorageService.deleteFile(oldPath);
                                log.info("Old {} file deleted for registration: {}", fileType, registrationId);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to delete old {} for registration: {}", fileType, registrationId, e);
                        }
                    }
                    // Upload new file using fileStorageService.uploadFile()
                    if (newFileBytes != null && newFileBytes.length > 0) {
                        try {
                            String fileName = fileStorageService.generateFileName(registrationId, fileType + ".file", fileType);
                            String newPath = fileStorageService.uploadFile(fileName, newFileBytes, contentType);

                            // Update registration document
                            if ("aadhar".equalsIgnoreCase(fileType)) {
                                doc.setAadharPhotoPath(newPath);
                            } else {
                                doc.setDocumentUploadPath(newPath);
                            }
                            registrationRepository.save(doc);

                            log.info("New {} file uploaded for registration: {} at path: {}", fileType, registrationId, newPath);
                            return newPath;
                        } catch (Exception e) {
                            log.error("Failed to upload new {} for registration: {}", fileType, registrationId, e);
                            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
                        }
                    } else {
                        throw new IllegalArgumentException("File content cannot be empty");
                    }
                })
                .orElseThrow(() -> new IllegalArgumentException("Registration not found: " + registrationId));
    }

    /**
     * Gets file information for a registration
     * Returns both file paths using generateFileName reference and their existence status using fileExists()
     *
     * @param registrationId Registration ID
     * @return Map with file information
     */
    public Map<String, Object> getRegistrationFileInfo(String registrationId) {
        return registrationRepository.findById(registrationId)
                .map(doc -> {
                    Map<String, Object> fileInfo = new HashMap<>();

                    // Aadhar photo info
                    Map<String, Object> aadharInfo = new HashMap<>();
                    aadharInfo.put("path", doc.getAadharPhotoPath());
                    if (doc.getAadharPhotoPath() != null && !doc.getAadharPhotoPath().isBlank()) {
                        aadharInfo.put("exists", fileStorageService.fileExists(doc.getAadharPhotoPath()));
                    } else {
                        aadharInfo.put("exists", false);
                    }
                    fileInfo.put("aadhar", aadharInfo);

                    // Document info
                    Map<String, Object> documentInfo = new HashMap<>();
                    documentInfo.put("path", doc.getDocumentUploadPath());
                    if (doc.getDocumentUploadPath() != null && !doc.getDocumentUploadPath().isBlank()) {
                        documentInfo.put("exists", fileStorageService.fileExists(doc.getDocumentUploadPath()));
                    } else {
                        documentInfo.put("exists", false);
                    }
                    fileInfo.put("document", documentInfo);

                    return fileInfo;
                })
                .orElseThrow(() -> new IllegalArgumentException("Registration not found: " + registrationId));
        }
    }







