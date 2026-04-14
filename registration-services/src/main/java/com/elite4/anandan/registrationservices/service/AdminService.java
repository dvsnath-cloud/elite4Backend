package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.document.RoomOnBoardDocument;
import com.elite4.anandan.registrationservices.dto.ClientAndRoomOnBoardId;
import com.elite4.anandan.registrationservices.dto.UserResponse;
import com.elite4.anandan.registrationservices.dto.ColiveListItem;
import com.elite4.anandan.registrationservices.dto.ColiveNameAndRooms;
import com.elite4.anandan.registrationservices.dto.Room;
import com.elite4.anandan.registrationservices.dto.BankDetails;
import com.elite4.anandan.registrationservices.dto.UpdateColiveBankDetailsRequest;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.RoleRepository;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import com.elite4.anandan.registrationservices.repository.RoomsOrHouseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoomsOrHouseRepository roomsOrHouseRepository;

    public AdminService(UserRepository userRepository, RoleRepository roleRepository, RoomsOrHouseRepository roomsOrHouseRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.roomsOrHouseRepository = roomsOrHouseRepository;
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

        // Remove duplicates
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
        user.setUpdatedAt(java.time.Instant.now());
        userRepository.save(user);

        // --- Notification logic: Approval ---
        try {
            String propertyNames = user.getClientDetails() != null
                    ? user.getClientDetails().stream().map(ClientAndRoomOnBoardId::getColiveName).collect(Collectors.joining(", "))
                    : "N/A";
            String subject = "Your CoLive Connect account is approved!";
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
        if(user.getRoleIds() != null) {
            user.getRoleIds().forEach(roleId -> {
                if (roleRepository.existsById(roleId)) {
                    roleRepository.deleteById(roleId);
                }
            });
        }
        // --- Notification logic: Rejection ---
        try {
            String subject = "CoLive Connect - Registration Rejected";
            String message = "Dear " + (user.getUsername() != null ? user.getUsername() : "User") + ", your CoLive Connect registration has been reviewed and rejected. Please contact support for more information.";
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
        user.setUpdatedAt(java.time.Instant.now());
        userRepository.save(user);

        // --- Notification logic: Deactivation ---
        try {
            String subject = "CoLive Connect - Account Deactivated";
            String message = "Dear " + (user.getUsername() != null ? user.getUsername() : "User") + ", your CoLive Connect account has been deactivated. Please contact the admin if you believe this is an error.";
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
        response.setClientDetails(user.getClientDetails());
        response.setPhoneNumber(user.getPhoneRaw());
        response.setPhoneNumberE164(user.getPhoneE164());
        
        // Fetch and populate rooms from clientDetails
        Set<String> rooms = new LinkedHashSet<>();
        Set<ColiveNameAndRooms> coliveNameAndRoomsSet = new LinkedHashSet<>();
        if (user.getClientDetails() != null && !user.getClientDetails().isEmpty()) {
            for (ClientAndRoomOnBoardId clientDetail : user.getClientDetails()) {
                ColiveNameAndRooms coliveNameAndRooms = new ColiveNameAndRooms();
                coliveNameAndRooms.setColiveName(clientDetail.getColiveName());
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
                                    rooms.add(room.getRoomNumber());
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
                coliveNameAndRooms.setBankDetailsList(clientDetail.getBankDetailsList());
                // Map license documents and photos from entity to DTO
                coliveNameAndRooms.setLicenseDocumentsPath(clientDetail.getLicenseDocumentsPath());
                coliveNameAndRooms.setUploadedPhotos(clientDetail.getUploadedPhotos());
                coliveNameAndRoomsSet.add(coliveNameAndRooms);
            }
        }
        response.setRooms(rooms);
        response.setClientNameAndRooms(coliveNameAndRoomsSet);
        
        // Fetch and populate roleNames
        List<String> roleNames = new ArrayList<>();
        if (user.getRoleIds() != null && !user.getRoleIds().isEmpty()) {
            for (String roleId : user.getRoleIds()) {
                if (roleRepository.existsById(roleId)) {
                    roleRepository.findById(roleId).ifPresent(role -> {
                        roleNames.add(role.getName().toString());
                    });
                }
            }
        }
        response.setRoleNames(roleNames);
        
        return response;
    }

    /**
     * Search for colive properties by name.
     */
    public List<ColiveListItem> searchColiveProperties(String search) {
        List<User> users = userRepository.findAll();
        List<ColiveListItem> items = new ArrayList<>();
        String searchLower = search.toLowerCase();
        for (User u : users) {
            boolean matched = false;
            // Match by username
            if (u.getUsername() != null && u.getUsername().toLowerCase().contains(searchLower)) {
                ColiveListItem item = new ColiveListItem();
                item.setColiveUserName(u.getUsername());
                item.setColiveName(u.getUsername());
                item.setCategoryType("PROPERTY");
                items.add(item);
                matched = true;
            }
            // Match by colive property names in clientDetails
            if (u.getClientDetails() != null) {
                for (var client : u.getClientDetails()) {
                    if (client.getColiveName() != null && client.getColiveName().toLowerCase().contains(searchLower)) {
                        ColiveListItem item = new ColiveListItem();
                        item.setColiveUserName(u.getUsername());
                        item.setColiveName(client.getColiveName());
                        item.setCategoryType("PROPERTY");
                        items.add(item);
                        matched = true;
                    }
                }
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
            ColiveNameAndRooms result = new ColiveNameAndRooms();
            result.setColiveName(coliveName);
            
            // Find the matching client detail for this colive
            if (userData.getClientDetails() != null && !userData.getClientDetails().isEmpty()) {
                for (ClientAndRoomOnBoardId clientDetail : userData.getClientDetails()) {
                    if (coliveName.equals(clientDetail.getColiveName())) {
                        // Set category type
                        String category = clientDetail.getClientCategory();
                        if (category != null) {
                            result.setCategoryType(ColiveNameAndRooms.categoryValues.valueOf(category));
                        }
                        
                        // Fetch and set rooms
                        String roomOnBoardId = clientDetail.getRoomOnBoardId();
                        if (roomOnBoardId != null && !roomOnBoardId.trim().isEmpty()) {
                            Optional<RoomOnBoardDocument> roomOnBoard = roomsOrHouseRepository.findById(roomOnBoardId);
                            if (roomOnBoard.isPresent()) {
                                Set<Room> roomSet = roomOnBoard.get().getRooms();
                                if (roomSet != null && !roomSet.isEmpty()) {
                                    result.setRooms(roomSet);
                                }
                            }
                        }
                        
                        // Set bank details
                        result.setBankDetailsList(clientDetail.getBankDetailsList());
                        
                        // Set uploaded photos
                        result.setUploadedPhotos(clientDetail.getUploadedPhotos());
                        
                        // Set license documents
                        result.setLicenseDocumentsPath(clientDetail.getLicenseDocumentsPath());
                        
                        return result;
                    }
                }
            }
            
            // If colive not found in client details, return with basic info
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

        // --- Notification logic: Property Added / Bank Details Updated ---
        try {
            String subject = "Bank Details Updated - CoLive Connect";
            String message = "Dear " + (user.getUsername() != null ? user.getUsername() : "User") + ", bank details for property '" + coliveName + "' have been successfully updated on your CoLive Connect account.";
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
}
