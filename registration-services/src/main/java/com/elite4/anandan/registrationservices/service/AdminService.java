package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.document.RoomOnBoardDocument;
import com.elite4.anandan.registrationservices.dto.BankDetails;
import com.elite4.anandan.registrationservices.dto.ClientAndRoomOnBoardId;
import com.elite4.anandan.registrationservices.dto.ColiveListItem;
import com.elite4.anandan.registrationservices.dto.ColiveNameAndRooms;
import com.elite4.anandan.registrationservices.dto.PropertyAccessAssignmentRequest;
import com.elite4.anandan.registrationservices.dto.PropertyAccessAssignmentResponse;
import com.elite4.anandan.registrationservices.dto.Room;
import com.elite4.anandan.registrationservices.dto.AdminGroupResponse;
import com.elite4.anandan.registrationservices.dto.BulkPropertyAccessRequest;
import com.elite4.anandan.registrationservices.dto.UpdateColiveBankDetailsRequest;
import com.elite4.anandan.registrationservices.dto.UserResponse;
import com.elite4.anandan.registrationservices.model.AdminGroup;
import com.elite4.anandan.registrationservices.model.EmployeeRole;
import com.elite4.anandan.registrationservices.model.Role;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.AdminGroupRepository;
import com.elite4.anandan.registrationservices.repository.RoleRepository;
import com.elite4.anandan.registrationservices.repository.RoomsOrHouseRepository;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminService {

    private static final String PROPERTY_ROLE_MODERATOR = "ROLE_MODERATOR";
    private static final String PROPERTY_ROLE_USER = "ROLE_USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoomsOrHouseRepository roomsOrHouseRepository;
    private final AdminGroupRepository adminGroupRepository;

    public AdminService(UserRepository userRepository, RoleRepository roleRepository,
                        RoomsOrHouseRepository roomsOrHouseRepository, AdminGroupRepository adminGroupRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.roomsOrHouseRepository = roomsOrHouseRepository;
        this.adminGroupRepository = adminGroupRepository;
    }

    @Autowired
    private NotificationClient notificationClient;

    /**
     * Get all users pending approval (inactive users).
     */
    public List<UserResponse> getPendingApprovals() {
        return userRepository.findAll()
                .stream()
                .filter(user -> !user.isActive())
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all active users.
     */
    public List<UserResponse> getActiveUsers() {
        return userRepository.findAll()
                .stream()
                .filter(User::isActive)
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get users with wildcard search on username, email, and phone number.
     * Supports partial matches (case-insensitive).
     */
    public List<UserResponse> getUserWithWildCard(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getActiveUsers();
        }

        List<User> users = new ArrayList<>();
        users.addAll(userRepository.findByUsernameContainingIgnoreCaseAndActiveTrue(searchTerm.trim()));
        users.addAll(userRepository.findByEmailContainingIgnoreCaseAndActiveTrue(searchTerm.trim()));
        users.addAll(userRepository.findByPhoneE164ContainingIgnoreCaseAndActiveTrue(searchTerm.trim()));

        Set<User> uniqueUsers = new LinkedHashSet<>(users);

        return uniqueUsers.stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    /**
     * Approve a user registration by setting active to true.
     */
    public ResponseEntity<?> approveUser(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = userOpt.get();
        if (user.isActive()) {
            return ResponseEntity.badRequest().body("User is already active");
        }

        user.setActive(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        try {
            String propertyNames = user.getClientDetails() != null
                    ? user.getClientDetails().stream().map(ClientAndRoomOnBoardId::getColiveName).collect(Collectors.joining(", "))
                    : "N/A";
            String subject = "Your CoLives Connect account is approved!";
            String message = "Dear " + (user.getUsername() != null ? user.getUsername() : "User") + ", your account has been approved by the moderator. Your properties: " + propertyNames + ". You can now log in and manage your colive.";
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                notificationClient.sendEmail(user.getEmail(), subject, message);
            }
            if (user.getPhoneRaw() != null && !user.getPhoneRaw().isBlank()) {
                notificationClient.sendSms(user.getPhoneRaw(), message);
                notificationClient.sendWhatsapp(user.getPhoneRaw(), message);
            }
        } catch (Exception ex) {
            log.warn("Failed to send approval notifications for {}: {}", username, ex.getMessage());
        }

        return ResponseEntity.ok(toUserResponse(user));
    }

    /**
     * Reject a user registration by deleting the user.
     */
    public ResponseEntity<?> rejectUser(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = userOpt.get();
        if (user.isActive()) {
            return ResponseEntity.badRequest().body("Cannot reject an active user");
        }
        if (user.getRoleIds() != null) {
            user.getRoleIds().forEach(roleId -> {
                if (roleRepository.existsById(roleId)) {
                    roleRepository.deleteById(roleId);
                }
            });
        }
        try {
            String subject = "CoLives Connect - Registration Rejected";
            String message = "Dear " + (user.getUsername() != null ? user.getUsername() : "User") + ", your CoLives Connect registration has been reviewed and rejected. Please contact support for more information.";
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                notificationClient.sendEmail(user.getEmail(), subject, message);
            }
            if (user.getPhoneRaw() != null && !user.getPhoneRaw().isBlank()) {
                notificationClient.sendSms(user.getPhoneRaw(), message);
                notificationClient.sendWhatsapp(user.getPhoneRaw(), message);
            }
        } catch (Exception ex) {
            log.warn("Failed to send rejection notifications for {}: {}", username, ex.getMessage());
        }

        userRepository.deleteById(user.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Deactivate an active user.
     */
    public ResponseEntity<?> deactivateUser(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = userOpt.get();
        if (!user.isActive()) {
            return ResponseEntity.badRequest().body("User is already inactive");
        }

        user.setActive(false);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        try {
            String subject = "CoLives Connect - Account Deactivated";
            String message = "Dear " + (user.getUsername() != null ? user.getUsername() : "User") + ", your CoLives Connect account has been deactivated. Please contact the admin if you believe this is an error.";
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                notificationClient.sendEmail(user.getEmail(), subject, message);
            }
            if (user.getPhoneRaw() != null && !user.getPhoneRaw().isBlank()) {
                notificationClient.sendSms(user.getPhoneRaw(), message);
                notificationClient.sendWhatsapp(user.getPhoneRaw(), message);
            }
        } catch (Exception ex) {
            log.warn("Failed to send deactivation notifications for {}: {}", username, ex.getMessage());
        }

        return ResponseEntity.ok(toUserResponse(user));
    }

    /**
     * Get user by ID.
     */
    public ResponseEntity<?> getUserById(String userId) {
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(toUserResponse(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    private UserResponse toUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRoleIds(user.getRoleIds());
        response.setActive(user.isActive());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        response.setLastLoginAt(user.getLastLoginAt());
        response.setOwnerOfClient(user.getOwnerOfClient());
        response.setPhoneNumber(user.getPhoneRaw());
        response.setPhoneNumberE164(user.getPhoneE164());

        Set<String> rooms = new LinkedHashSet<>();
        Set<ColiveNameAndRooms> coliveNameAndRoomsSet = new LinkedHashSet<>();
        Set<ClientAndRoomOnBoardId> normalizedClientDetails = new LinkedHashSet<>();
        if (user.getClientDetails() != null && !user.getClientDetails().isEmpty()) {
            for (ClientAndRoomOnBoardId clientDetail : user.getClientDetails()) {
                normalizedClientDetails.add(copyPropertyAssignment(user, clientDetail, resolveAccessRole(user, clientDetail)));
                coliveNameAndRoomsSet.add(toColiveNameAndRooms(user, clientDetail, rooms));
            }
        }
        response.setClientDetails(normalizedClientDetails);
        response.setRooms(rooms);
        response.setClientNameAndRooms(coliveNameAndRoomsSet);

        List<String> roleNames = new ArrayList<>();
        if (user.getRoleIds() != null && !user.getRoleIds().isEmpty()) {
            for (String roleId : user.getRoleIds()) {
                roleRepository.findById(roleId).ifPresent(role -> roleNames.add(role.getName().toString()));
            }
        }
        response.setRoleNames(roleNames);

        return response;
    }

    private ColiveNameAndRooms toColiveNameAndRooms(User user, ClientAndRoomOnBoardId clientDetail, Set<String> roomsAccumulator) {
        ColiveNameAndRooms coliveNameAndRooms = new ColiveNameAndRooms();
        coliveNameAndRooms.setColiveName(clientDetail.getColiveName());
        coliveNameAndRooms.setOwnerUsername(resolveOwnerUsername(user, clientDetail));
        coliveNameAndRooms.setAccessRole(resolveAccessRole(user, clientDetail));

        Set<Room> roomNumbers = new LinkedHashSet<>();
        String roomOnBoardId = clientDetail.getRoomOnBoardId();
        if (roomOnBoardId != null && !roomOnBoardId.trim().isEmpty()) {
            Optional<RoomOnBoardDocument> roomOnBoard = roomsOrHouseRepository.findById(roomOnBoardId);
            if (roomOnBoard.isPresent()) {
                Set<Room> roomSet = roomOnBoard.get().getRooms();
                if (roomSet != null && !roomSet.isEmpty()) {
                    roomNumbers.addAll(roomSet);
                    for (Room room : roomSet) {
                        if (room.getRoomNumber() != null) {
                            roomsAccumulator.add(room.getRoomNumber());
                        }
                    }
                }
            }
        }

        coliveNameAndRooms.setRooms(roomNumbers);
        String category = clientDetail.getClientCategory();
        if (category != null) {
            coliveNameAndRooms.setCategoryType(ColiveNameAndRooms.categoryValues.valueOf(category));
        }
        coliveNameAndRooms.setBankDetailsList(copyBankDetails(clientDetail.getBankDetailsList()));
        coliveNameAndRooms.setPanNumber(clientDetail.getPanNumber());
        coliveNameAndRooms.setGstNumber(clientDetail.getGstNumber());
        coliveNameAndRooms.setLegalBusinessName(clientDetail.getLegalBusinessName());
        coliveNameAndRooms.setBusinessType(clientDetail.getBusinessType());
        coliveNameAndRooms.setBusinessAddress(clientDetail.getBusinessAddress());
        coliveNameAndRooms.setDocumentType(clientDetail.getDocumentType());
        coliveNameAndRooms.setDocumentNumber(clientDetail.getDocumentNumber());
        coliveNameAndRooms.setLicenseDocumentsPath(copyStringList(clientDetail.getLicenseDocumentsPath()));
        coliveNameAndRooms.setUploadedPhotos(copyStringList(clientDetail.getUploadedPhotos()));
        return coliveNameAndRooms;
    }

    /**
     * Search for colive properties by name.
     */
    public List<ColiveListItem> searchColiveProperties(String search) {
        String searchLower = search == null ? "" : search.toLowerCase().trim();
        List<ColiveListItem> items = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (User user : userRepository.findAll()) {
            if (user.getClientDetails() == null || user.getClientDetails().isEmpty()) {
                continue;
            }

            for (ClientAndRoomOnBoardId client : user.getClientDetails()) {
                if (client.getColiveName() == null || client.getColiveName().isBlank()) {
                    continue;
                }

                String ownerUsername = resolveOwnerUsername(user, client);
                if (ownerUsername == null || ownerUsername.isBlank()) {
                    continue;
                }

                boolean matchesSearch = searchLower.isBlank()
                        || client.getColiveName().toLowerCase().contains(searchLower)
                        || ownerUsername.toLowerCase().contains(searchLower)
                        || (user.getUsername() != null && user.getUsername().toLowerCase().contains(searchLower));
                if (!matchesSearch) {
                    continue;
                }

                String key = ownerUsername + "::" + client.getColiveName();
                if (!seen.add(key)) {
                    continue;
                }

                ColiveListItem item = new ColiveListItem();
                item.setColiveUserName(ownerUsername);
                item.setColiveName(client.getColiveName());
                item.setCategoryType("PROPERTY");
                items.add(item);
            }
        }

        return items;
    }

    /**
     * Get user with clients but without rooms details.
     */
    public UserResponse getUserClientsWithOutRooms(String username) {
        Optional<User> user = userRepository.findByUsername(username);
        return user.map(this::toUserResponse).orElse(null);
    }

    /**
     * Get user with clients, rooms, and uploaded photos/attachments.
     */
    public ColiveNameAndRooms getUserClientsWithRoomsAndUploadedPhotosAndAttachments(String username, String coliveName) {
        Optional<User> user = userRepository.findByUsername(username);
        if (user.isPresent()) {
            User userData = user.get();
            if (userData.getClientDetails() != null && !userData.getClientDetails().isEmpty()) {
                for (ClientAndRoomOnBoardId clientDetail : userData.getClientDetails()) {
                    if (coliveName.equals(clientDetail.getColiveName())) {
                        return toColiveNameAndRooms(userData, clientDetail, new LinkedHashSet<>());
                    }
                }
            }

            ColiveNameAndRooms result = new ColiveNameAndRooms();
            result.setColiveName(coliveName);
            result.setCategoryType(ColiveNameAndRooms.categoryValues.FLAT);
            return result;
        }
        return null;
    }

    /**
     * Get user with all details including roles.
     */
    public UserResponse getUserWithRoles(String username) {
        Optional<User> user = userRepository.findByUsername(username);
        return user.map(this::toUserResponse).orElse(null);
    }

    public List<PropertyAccessAssignmentResponse> getPropertyAccessAssignments(String ownerUsername, String coliveName) {
        if (ownerUsername == null || ownerUsername.isBlank() || coliveName == null || coliveName.isBlank()) {
            return new ArrayList<>();
        }

        Optional<User> ownerOpt = userRepository.findByUsername(ownerUsername);
        if (ownerOpt.isEmpty()) {
            return new ArrayList<>();
        }

        User ownerUser = ownerOpt.get();
        ClientAndRoomOnBoardId ownerAssignment = findPropertyAssignment(ownerUser, ownerUsername, coliveName);
        if (ownerAssignment == null) {
            return new ArrayList<>();
        }

        LinkedHashMap<String, PropertyAccessAssignmentResponse> assignments = new LinkedHashMap<>();
        assignments.put(ownerUser.getUsername(), toPropertyAccessAssignment(ownerUser, ownerAssignment));

        for (User user : userRepository.findAll()) {
            if (user.getUsername() == null || Objects.equals(user.getUsername(), ownerUsername)) {
                continue;
            }
            ClientAndRoomOnBoardId assignment = findPropertyAssignment(user, ownerUsername, coliveName);
            if (assignment != null) {
                assignments.put(user.getUsername(), toPropertyAccessAssignment(user, assignment));
            }
        }

        return new ArrayList<>(assignments.values());
    }

    public ResponseEntity<?> upsertPropertyAccess(PropertyAccessAssignmentRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body("Request body is required");
        }
        if (request.getOwnerUsername() == null || request.getOwnerUsername().isBlank()) {
            return ResponseEntity.badRequest().body("ownerUsername is required");
        }
        if (request.getColiveName() == null || request.getColiveName().isBlank()) {
            return ResponseEntity.badRequest().body("coliveName is required");
        }
        if (request.getTargetUsername() == null || request.getTargetUsername().isBlank()) {
            return ResponseEntity.badRequest().body("targetUsername is required");
        }

        String normalizedRole = normalizeAccessRole(request.getAccessRole());
        Optional<User> ownerOpt = userRepository.findByUsername(request.getOwnerUsername().trim());
        if (ownerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User ownerUser = ownerOpt.get();
        ClientAndRoomOnBoardId sourceAssignment = findPropertyAssignment(ownerUser, request.getOwnerUsername().trim(), request.getColiveName().trim());
        if (sourceAssignment == null) {
            return ResponseEntity.badRequest().body("Property '" + request.getColiveName() + "' not found for owner '" + request.getOwnerUsername() + "'");
        }

        Optional<User> targetOpt = userRepository.findByUsername(request.getTargetUsername().trim());
        if (targetOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Target user '" + request.getTargetUsername() + "' not found");
        }

        User targetUser = targetOpt.get();
        boolean ownerAssignment = Objects.equals(targetUser.getUsername(), ownerUser.getUsername());
        if (ownerAssignment && !PROPERTY_ROLE_MODERATOR.equals(normalizedRole)) {
            return ResponseEntity.badRequest().body("Original owner must remain a moderator for this property");
        }

        Set<ClientAndRoomOnBoardId> updatedAssignments = targetUser.getClientDetails() != null
                ? new LinkedHashSet<>(targetUser.getClientDetails())
                : new LinkedHashSet<>();

        ClientAndRoomOnBoardId existingAssignment = findPropertyAssignment(targetUser, request.getOwnerUsername().trim(), request.getColiveName().trim());
        if (existingAssignment != null) {
            updatedAssignments.remove(existingAssignment);
        }

        updatedAssignments.add(copyPropertyAssignment(ownerUser, sourceAssignment, normalizedRole));
        targetUser.setClientDetails(updatedAssignments);
        targetUser.setUpdatedAt(Instant.now());
        reconcilePropertyScopedRoles(targetUser);

        User savedUser = userRepository.save(targetUser);
        ClientAndRoomOnBoardId savedAssignment = findPropertyAssignment(savedUser, request.getOwnerUsername().trim(), request.getColiveName().trim());
        return ResponseEntity.ok(toPropertyAccessAssignment(savedUser, savedAssignment));
    }

    public ResponseEntity<?> removePropertyAccess(PropertyAccessAssignmentRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body("Request body is required");
        }
        if (request.getOwnerUsername() == null || request.getOwnerUsername().isBlank()) {
            return ResponseEntity.badRequest().body("ownerUsername is required");
        }
        if (request.getColiveName() == null || request.getColiveName().isBlank()) {
            return ResponseEntity.badRequest().body("coliveName is required");
        }
        if (request.getTargetUsername() == null || request.getTargetUsername().isBlank()) {
            return ResponseEntity.badRequest().body("targetUsername is required");
        }

        if (Objects.equals(request.getOwnerUsername().trim(), request.getTargetUsername().trim())) {
            return ResponseEntity.badRequest().body("Original owner access cannot be removed from their property");
        }

        Optional<User> targetOpt = userRepository.findByUsername(request.getTargetUsername().trim());
        if (targetOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Target user '" + request.getTargetUsername() + "' not found");
        }

        User targetUser = targetOpt.get();
        if (targetUser.getClientDetails() == null || targetUser.getClientDetails().isEmpty()) {
            return ResponseEntity.badRequest().body("User does not have any properties assigned");
        }

        boolean removedAny = false;
        Set<ClientAndRoomOnBoardId> updatedAssignments = new LinkedHashSet<>();
        for (ClientAndRoomOnBoardId clientDetail : targetUser.getClientDetails()) {
            if (isMatchingPropertyAssignment(targetUser, clientDetail, request.getOwnerUsername().trim(), request.getColiveName().trim())) {
                removedAny = true;
                continue;
            }
            updatedAssignments.add(clientDetail);
        }

        if (!removedAny) {
            return ResponseEntity.badRequest().body("Property assignment not found for target user");
        }

        targetUser.setClientDetails(updatedAssignments);
        targetUser.setUpdatedAt(Instant.now());
        reconcilePropertyScopedRoles(targetUser);
        userRepository.save(targetUser);
        return ResponseEntity.noContent().build();
    }

    public ResponseEntity<?> addBankDetailsToColive(String username, String coliveName,
                                                    UpdateColiveBankDetailsRequest request) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (coliveName == null || coliveName.isBlank()) {
            return ResponseEntity.badRequest().body("coliveName is required");
        }
        if (request.getBankName() == null || request.getBankName().isBlank()) {
            return ResponseEntity.badRequest().body("bankName is required");
        }
        if (request.getAccountNumber() == null || request.getAccountNumber().isBlank()) {
            return ResponseEntity.badRequest().body("accountNumber is required");
        }
        if (request.getIfscCode() == null || request.getIfscCode().isBlank()) {
            return ResponseEntity.badRequest().body("ifscCode is required");
        }
        if (request.getBeneficiaryName() == null || request.getBeneficiaryName().isBlank()) {
            return ResponseEntity.badRequest().body("beneficiaryName is required");
        }

        User user = userOpt.get();
        Set<ClientAndRoomOnBoardId> clientDetails = user.getClientDetails();
        if (clientDetails == null || clientDetails.isEmpty()) {
            return ResponseEntity.badRequest().body("User does not have any properties assigned");
        }

        ClientAndRoomOnBoardId targetClient = null;
        for (ClientAndRoomOnBoardId clientDetail : clientDetails) {
            if (coliveName.equals(clientDetail.getColiveName())) {
                targetClient = clientDetail;
                break;
            }
        }

        if (targetClient == null) {
            return ResponseEntity.badRequest().body("Property '" + coliveName + "' not found for user '" + username + "'");
        }

        List<BankDetails> existingBankDetails = targetClient.getBankDetailsList() != null
                ? new ArrayList<>(targetClient.getBankDetailsList())
                : new ArrayList<>();

        Iterator<BankDetails> iterator = existingBankDetails.iterator();
        while (iterator.hasNext()) {
            BankDetails existing = iterator.next();
            if (request.getAccountNumber().equals(existing.getAccountNumber())) {
                iterator.remove();
                break;
            }
        }

        BankDetails bankDetails = new BankDetails();
        bankDetails.setBankName(request.getBankName());
        bankDetails.setAccountNumber(request.getAccountNumber());
        bankDetails.setIfscCode(request.getIfscCode());
        bankDetails.setAccountHolderName(request.getBeneficiaryName());
        bankDetails.setBranchCode(
                request.getBranchCode() != null && !request.getBranchCode().isBlank()
                        ? request.getBranchCode()
                        : request.getIfscCode()
        );
        bankDetails.setBranchAddress(request.getBranchAddress());
        bankDetails.setUpiId(request.getUpiId() != null ? request.getUpiId() : "");

        existingBankDetails.add(bankDetails);
        targetClient.setBankDetailsList(existingBankDetails);

        user.setUpdatedAt(Instant.now());
        User saved = userRepository.save(user);

        try {
            String subject = "Bank Details Updated - CoLives Connect";
            String message = "Dear " + (user.getUsername() != null ? user.getUsername() : "User") + ", bank details for property '" + coliveName + "' have been successfully updated on your CoLives Connect account.";
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                notificationClient.sendEmail(user.getEmail(), subject, message);
            }
            if (user.getPhoneRaw() != null && !user.getPhoneRaw().isBlank()) {
                notificationClient.sendSms(user.getPhoneRaw(), message);
                notificationClient.sendWhatsapp(user.getPhoneRaw(), message);
            }
        } catch (Exception ex) {
            log.warn("Failed to send bank details update notifications for {}: {}", username, ex.getMessage());
        }

        return ResponseEntity.ok(toUserResponse(saved));
    }

    private boolean isMatchingPropertyAssignment(User user, ClientAndRoomOnBoardId clientDetail, String ownerUsername, String coliveName) {
        return Objects.equals(resolveOwnerUsername(user, clientDetail), ownerUsername)
                && Objects.equals(clientDetail.getColiveName(), coliveName);
    }

    private ClientAndRoomOnBoardId findPropertyAssignment(User user, String ownerUsername, String coliveName) {
        if (user == null || user.getClientDetails() == null || user.getClientDetails().isEmpty()) {
            return null;
        }
        for (ClientAndRoomOnBoardId clientDetail : user.getClientDetails()) {
            if (isMatchingPropertyAssignment(user, clientDetail, ownerUsername, coliveName)) {
                return clientDetail;
            }
        }
        return null;
    }

    private PropertyAccessAssignmentResponse toPropertyAccessAssignment(User user, ClientAndRoomOnBoardId assignment) {
        if (user == null || assignment == null) {
            return null;
        }
        String ownerUsername = resolveOwnerUsername(user, assignment);
        boolean ownerAssignment = Objects.equals(ownerUsername, user.getUsername());
        return new PropertyAccessAssignmentResponse(
                ownerUsername + "::" + assignment.getColiveName() + "::" + user.getUsername(),
                user.getUsername(),
                user.getUsername(),
                user.getEmail(),
                user.getPhoneRaw(),
                user.isActive(),
                ownerUsername,
                assignment.getColiveName(),
                resolveAccessRole(user, assignment),
                ownerAssignment
        );
    }

    private ClientAndRoomOnBoardId copyPropertyAssignment(User ownerUser, ClientAndRoomOnBoardId source, String accessRole) {
        ClientAndRoomOnBoardId copy = new ClientAndRoomOnBoardId();
        copy.setColiveName(source.getColiveName());
        copy.setRoomOnBoardId(source.getRoomOnBoardId());
        copy.setClientCategory(source.getClientCategory());
        copy.setOwnerUsername(resolveOwnerUsername(ownerUser, source));
        copy.setAccessRole(normalizeAccessRole(accessRole));
        copy.setBankDetailsList(copyBankDetails(source.getBankDetailsList()));
        copy.setPanNumber(source.getPanNumber());
        copy.setGstNumber(source.getGstNumber());
        copy.setLegalBusinessName(source.getLegalBusinessName());
        copy.setBusinessType(source.getBusinessType());
        copy.setBusinessAddress(source.getBusinessAddress());
        copy.setLicenseDocumentsPath(copyStringList(source.getLicenseDocumentsPath()));
        copy.setDocumentType(source.getDocumentType());
        copy.setDocumentNumber(source.getDocumentNumber());
        copy.setUploadedPhotos(copyStringList(source.getUploadedPhotos()));
        copy.setAadharPhotoPath(source.getAadharPhotoPath());
        copy.setDocumentUploadPath(source.getDocumentUploadPath());
        return copy;
    }

    private List<String> copyStringList(List<String> values) {
        return values == null ? null : new ArrayList<>(values);
    }

    private List<BankDetails> copyBankDetails(List<BankDetails> bankDetailsList) {
        if (bankDetailsList == null) {
            return null;
        }

        List<BankDetails> copied = new ArrayList<>();
        for (BankDetails bankDetails : bankDetailsList) {
            copied.add(new BankDetails(
                    bankDetails.getAccountHolderName(),
                    bankDetails.getAccountNumber(),
                    bankDetails.getBankName(),
                    bankDetails.getBranchCode(),
                    bankDetails.getIfscCode(),
                    bankDetails.getBranchAddress(),
                    bankDetails.getUpiId()
            ));
        }
        return copied;
    }

    private String resolveOwnerUsername(User user, ClientAndRoomOnBoardId clientDetail) {
        if (clientDetail.getOwnerUsername() != null && !clientDetail.getOwnerUsername().isBlank()) {
            return clientDetail.getOwnerUsername();
        }
        return user != null ? user.getUsername() : null;
    }

    private String resolveAccessRole(User user, ClientAndRoomOnBoardId clientDetail) {
        if (clientDetail.getAccessRole() != null && !clientDetail.getAccessRole().isBlank()) {
            return normalizeAccessRole(clientDetail.getAccessRole());
        }
        String ownerUsername = resolveOwnerUsername(user, clientDetail);
        if (ownerUsername != null && user != null && Objects.equals(ownerUsername, user.getUsername())) {
            return PROPERTY_ROLE_MODERATOR;
        }
        return PROPERTY_ROLE_USER;
    }

    private String normalizeAccessRole(String accessRole) {
        if (accessRole == null || accessRole.isBlank()) {
            return PROPERTY_ROLE_USER;
        }

        String normalized = accessRole.trim().toUpperCase();
        if ("MODERATOR".equals(normalized)) {
            return PROPERTY_ROLE_MODERATOR;
        }
        if ("USER".equals(normalized)) {
            return PROPERTY_ROLE_USER;
        }
        if (PROPERTY_ROLE_MODERATOR.equals(normalized)) {
            return PROPERTY_ROLE_MODERATOR;
        }
        return PROPERTY_ROLE_USER;
    }

    /**
     * Reconciles global roleIds for a user based on their property assignments.
     *
     * ROLE_MODERATOR is added to user.roleIds when the user has at least one property
     * assignment with accessRole=ROLE_MODERATOR. This is required so Spring Security
     * @PreAuthorize("hasAnyRole('MODERATOR')") endpoints remain accessible.
     *
     * NOTE: The global ROLE_MODERATOR here grants API-level access. Per-property
     * management rights (which property a user can manage in the UI) are governed
     * solely by ClientAndRoomOnBoardId.accessRole — not by this global role.
     * The frontend must use getPropertyRoles(property) for per-property UI decisions.
     */
    private void reconcilePropertyScopedRoles(User user) {
        Set<String> roleIds = user.getRoleIds() != null ? new LinkedHashSet<>(user.getRoleIds()) : new LinkedHashSet<>();
        Role userRole = getOrCreateRole(EmployeeRole.ROLE_USER);
        roleIds.add(userRole.getId());

        Role moderatorRole = getOrCreateRole(EmployeeRole.ROLE_MODERATOR);
        boolean hasModeratorAssignment = user.getClientDetails() != null
                && user.getClientDetails().stream().anyMatch(clientDetail -> PROPERTY_ROLE_MODERATOR.equals(resolveAccessRole(user, clientDetail)));
        if (hasModeratorAssignment) {
            roleIds.add(moderatorRole.getId());
        } else {
            roleIds.remove(moderatorRole.getId());
        }
        user.setRoleIds(roleIds);
        log.debug("🔒 reconcilePropertyScopedRoles: user={} roleIds={}", user.getUsername(), roleIds);
    }

    private Role getOrCreateRole(EmployeeRole employeeRole) {
        List<Role> existing = roleRepository.findAllByName(employeeRole);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        Role role = new Role();
        role.setName(employeeRole);
        return roleRepository.save(role);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Admin Group management  (reuses existing User/Role infrastructure)
    // ══════════════════════════════════════════════════════════════════════════

    public List<AdminGroupResponse> listAdminGroups() {
        return adminGroupRepository.findAll().stream()
                .map(this::toAdminGroupResponse)
                .collect(Collectors.toList());
    }

    public ResponseEntity<?> createAdminGroup(String groupName, String description, String createdBy) {
        if (groupName == null || groupName.isBlank())
            return ResponseEntity.badRequest().body("groupName is required");
        if (adminGroupRepository.findByGroupName(groupName.trim()).isPresent())
            return ResponseEntity.badRequest().body("Group '" + groupName + "' already exists");

        AdminGroup group = new AdminGroup();
        group.setGroupName(groupName.trim());
        group.setDescription(description);
        group.setCreatedBy(createdBy);
        return ResponseEntity.ok(toAdminGroupResponse(adminGroupRepository.save(group)));
    }

    public ResponseEntity<?> deleteAdminGroup(String groupId) {
        Optional<AdminGroup> opt = adminGroupRepository.findById(groupId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        if (opt.get().isSuperAdmin())
            return ResponseEntity.badRequest().body("Cannot delete the super-admin group");
        adminGroupRepository.deleteById(groupId);
        return ResponseEntity.noContent().build();
    }

    /** Add or remove a member username from a group. action = "add" | "remove" */
    public ResponseEntity<?> updateGroupMember(String groupId, String username, String action) {
        Optional<AdminGroup> opt = adminGroupRepository.findById(groupId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        if (userRepository.findByUsername(username).isEmpty())
            return ResponseEntity.badRequest().body("User '" + username + "' not found");

        AdminGroup group = opt.get();
        Set<String> members = group.getMemberUsernames() != null
                ? new LinkedHashSet<>(group.getMemberUsernames()) : new LinkedHashSet<>();

        if ("add".equalsIgnoreCase(action)) {
            members.add(username);
        } else if ("remove".equalsIgnoreCase(action)) {
            if (group.isSuperAdmin() && members.size() == 1)
                return ResponseEntity.badRequest().body("Super-admin group must have at least one member");
            members.remove(username);
        } else {
            return ResponseEntity.badRequest().body("action must be 'add' or 'remove'");
        }
        group.setMemberUsernames(members);
        group.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(toAdminGroupResponse(adminGroupRepository.save(group)));
    }

    private AdminGroupResponse toAdminGroupResponse(AdminGroup g) {
        AdminGroupResponse r = new AdminGroupResponse();
        r.setId(g.getId());
        r.setGroupName(g.getGroupName());
        r.setDescription(g.getDescription());
        r.setSuperAdmin(g.isSuperAdmin());
        r.setMemberUsernames(g.getMemberUsernames());
        r.setMemberCount(g.getMemberUsernames() != null ? g.getMemberUsernames().size() : 0);
        r.setCreatedBy(g.getCreatedBy());
        r.setCreatedAt(g.getCreatedAt());
        r.setUpdatedAt(g.getUpdatedAt());
        return r;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Bulk property-access  (reuses existing upsertPropertyAccess per entry)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Assign one user to many properties in a single call.
     * Delegates to the existing upsertPropertyAccess() for each entry so all
     * validation, role reconciliation and DB logic is fully reused.
     */
    public ResponseEntity<?> bulkUpsertPropertyAccess(BulkPropertyAccessRequest req) {
        if (req.getAssignments() == null || req.getAssignments().isEmpty())
            return ResponseEntity.badRequest().body("assignments list is required");

        List<Object> results = new ArrayList<>();
        List<String> errors  = new ArrayList<>();

        for (PropertyAccessAssignmentRequest a : req.getAssignments()) {
            // Stamp the canonical targetUsername from the top-level field
            a.setTargetUsername(req.getTargetUsername());
            ResponseEntity<?> r = upsertPropertyAccess(a);
            if (r.getStatusCode().is2xxSuccessful()) {
                results.add(r.getBody());
            } else {
                errors.add(a.getColiveName() + ": " + r.getBody());
            }
        }

        if (!errors.isEmpty())
            return ResponseEntity.status(207).body(
                    java.util.Map.of("succeeded", results, "failed", errors));
        return ResponseEntity.ok(results);
    }
}
