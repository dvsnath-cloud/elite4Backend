package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.document.RoomOnBoardDocument;
import com.elite4.anandan.registrationservices.dto.ClientAndRoomOnBoardId;
import com.elite4.anandan.registrationservices.dto.ColiveListItem;
import com.elite4.anandan.registrationservices.dto.ColiveNameAndRooms;
import com.elite4.anandan.registrationservices.dto.Room;
import com.elite4.anandan.registrationservices.dto.UserResponse;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.RoleRepository;
import com.elite4.anandan.registrationservices.repository.RoomsOrHouseRepository;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for admin operations on user accounts.
 */
@Service
public class AdminService {

    private final UserRepository userRepository;

    private final RoleRepository roleRepository;

    private final RoomsOrHouseRepository roomsOrHouseRepository;

    public AdminService(UserRepository userRepository, RoleRepository roleRepository, RoomsOrHouseRepository roomsOrHouseRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.roomsOrHouseRepository = roomsOrHouseRepository;
    }

    /**
     * Search active CoLive properties by name (case-insensitive contains match).
     * Returns matching property names only (no rooms). Limited to 20 results.
     * Rooms are fetched separately after the user selects a property.
     */
    public List<ColiveListItem> searchColiveProperties(String search) {
        if (search == null || search.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String lowerSearch = search.trim().toLowerCase();
        List<ColiveListItem> result = new ArrayList<>();
        List<User> activeUsers = userRepository.findAll().stream()
                .filter(User::isActive)
                .filter(u -> u.getClientDetails() != null && !u.getClientDetails().isEmpty())
                .collect(Collectors.toList());

        for (User user : activeUsers) {
            for (ClientAndRoomOnBoardId client : user.getClientDetails()) {
                if (client.getColiveName() == null ||
                        !client.getColiveName().toLowerCase().contains(lowerSearch)) {
                    continue;
                }

                result.add(new ColiveListItem(
                        user.getUsername(),
                        client.getColiveName(),
                        client.getClientCategory(),
                        Collections.emptyList()
                ));

                if (result.size() >= 20) {
                    return result;
                }
            }
        }
        return result;
    }

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

    public UserResponse getUserClientsWithOutRooms(String username){
        User activeUsers = userRepository.findAll()
                .stream()
                .filter(User::isActive)
                .filter(u -> u.getUsername().equals(username))
                .findFirst()
                .orElse(null);
        List<String> roleNames =  new ArrayList<>();
        assert activeUsers != null;
        Set<String> roleIds =  activeUsers.getRoleIds();
        for(String roleId : roleIds) {
            if(roleRepository.existsById(roleId)) {
                roleRepository.findById(roleId).ifPresent(role -> {
                    roleNames.add(role.getName().toString());
                });
            }
        }
        return toUserResponseWithOutRooms(activeUsers,roleNames);
    }

    public ColiveNameAndRooms getUserClientsWithRoomsAndUploadedPhotosAndAttachments(String username,String coLiveName) {
        User activeUsers = userRepository.findAll()
                .stream()
                .filter(User::isActive)
                .filter(u -> u.getUsername().equals(username))
                .findFirst()
                .orElse(null);
        List<String> roleNames = new ArrayList<>();
        assert activeUsers != null;
        Set<String> roleIds = activeUsers.getRoleIds();
        for (String roleId : roleIds) {
            if (roleRepository.existsById(roleId)) {
                roleRepository.findById(roleId).ifPresent(role -> {
                    roleNames.add(role.getName().toString());
                });
            }
        }
        ColiveNameAndRooms coliveNameAndRooms = new ColiveNameAndRooms();
        Set<ClientAndRoomOnBoardId> clientAndRoomOnBoardIds = activeUsers.getClientDetails();
        if (clientAndRoomOnBoardIds != null) {
            for (ClientAndRoomOnBoardId clientAndRoomOnBoardId : clientAndRoomOnBoardIds) {
                if (clientAndRoomOnBoardId.getColiveName().equals(coLiveName)) {
                    coliveNameAndRooms.setColiveName(clientAndRoomOnBoardId.getColiveName());
                    coliveNameAndRooms.setLicenseDocumentsPath(clientAndRoomOnBoardId.getLicenseDocumentsPath());
                    coliveNameAndRooms.setUploadedPhotos(clientAndRoomOnBoardId.getUploadedPhotos());
                    String category = clientAndRoomOnBoardId.getClientCategory();
                    if (category == null || category.trim().isEmpty()) {
                        coliveNameAndRooms.setCategoryType(null);
                    } else {
                        coliveNameAndRooms.setCategoryType(ColiveNameAndRooms.categoryValues.valueOf(category));
                    }
                    /*get rooms for this specific coLiveName */
                    String roomOnBoardId = clientAndRoomOnBoardId.getRoomOnBoardId();
                    if (roomOnBoardId != null && !roomOnBoardId.trim().isEmpty()) {
                        Optional<RoomOnBoardDocument> roomOnBoardDocument = roomsOrHouseRepository.findById(roomOnBoardId);
                        if (roomOnBoardDocument.isPresent()) {
                            Set<Room> retrievedRooms = roomOnBoardDocument.get().getRooms();
                            // Handle null rooms safely
                            if (retrievedRooms != null && !retrievedRooms.isEmpty()) {
                                coliveNameAndRooms.setRooms(retrievedRooms);
                            }

                        }
                    }
                }
            }
        }
        return coliveNameAndRooms;
    }

    public UserResponse getUserWithRoles(String username){
        User activeUsers = userRepository.findAll()
                .stream()
                .filter(User::isActive)
                .filter(u -> u.getUsername().equals(username))
                .findFirst()
                .orElse(null);
        List<String> roleNames =  new ArrayList<>();
        assert activeUsers != null;
        Set<String> roleIds =  activeUsers.getRoleIds();
        for(String roleId : roleIds) {
            if(roleRepository.existsById(roleId)) {
                roleRepository.findById(roleId).ifPresent(role -> {
                    roleNames.add(role.getName().toString());
                });
            }
        }
        return toUserResponseWithRoleNames(activeUsers,roleNames);
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

    public UserResponse toUserResponseWithRoleNames(User user,List<String> roleNames) {
        UserResponse response = toUserResponse(user);
        response.setRoleNames(roleNames);
        return response;
    }

    public UserResponse toUserResponseWithOutRooms(User user,List<String> roleNames) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRoleIds(user.getRoleIds());
        response.setActive(user.isActive());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        response.setLastLoginAt(user.getLastLoginAt());
        response.setPhoneNumber(user.getPhoneRaw());
        response.setAadharPhotoPath(user.getAadharPhotoPath());
        Set<ColiveNameAndRooms> coliveNameAndRoomsSet = new HashSet<>();
        Set<ClientAndRoomOnBoardId> clientAndRoomOnBoardIds = user.getClientDetails();
        if (clientAndRoomOnBoardIds != null) {
            for(ClientAndRoomOnBoardId clientAndRoomOnBoardId : clientAndRoomOnBoardIds) {
                ColiveNameAndRooms coliveNameAndRooms = new ColiveNameAndRooms();
                coliveNameAndRooms.setColiveName(clientAndRoomOnBoardId.getColiveName());
                coliveNameAndRooms.setLicenseDocumentsPath(clientAndRoomOnBoardId.getLicenseDocumentsPath());
                coliveNameAndRooms.setUploadedPhotos(clientAndRoomOnBoardId.getUploadedPhotos());
                String category = clientAndRoomOnBoardId.getClientCategory();
                if(category == null || category.trim().isEmpty()) {
                    coliveNameAndRooms.setCategoryType(null);
                } else {
                    coliveNameAndRooms.setCategoryType(ColiveNameAndRooms.categoryValues.valueOf(category));}
                coliveNameAndRoomsSet.add(coliveNameAndRooms);
            }
        }
        response.setClientNameAndRooms(coliveNameAndRoomsSet);
        response.setRoleNames(roleNames);
        return response;
    }

    public UserResponse toUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRoleIds(user.getRoleIds());
        response.setActive(user.isActive());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        response.setLastLoginAt(user.getLastLoginAt());
        response.setPhoneNumber(user.getPhoneRaw());
        response.setAadharPhotoPath(user.getAadharPhotoPath());
        Set<ColiveNameAndRooms> coliveNameAndRoomsSet = new HashSet<>();
        Set<ClientAndRoomOnBoardId> clientAndRoomOnBoardIds = user.getClientDetails();
        if (clientAndRoomOnBoardIds != null) {
            for(ClientAndRoomOnBoardId clientAndRoomOnBoardId : clientAndRoomOnBoardIds) {
                ColiveNameAndRooms coliveNameAndRooms = new ColiveNameAndRooms();
                coliveNameAndRooms.setColiveName(clientAndRoomOnBoardId.getColiveName());
                Set<Room> roomNumbers = new HashSet<>();
                // Only fetch rooms for this specific client, not all
                String roomOnBoardId = clientAndRoomOnBoardId.getRoomOnBoardId();
                if (roomOnBoardId != null && !roomOnBoardId.trim().isEmpty()) {
                    Optional<RoomOnBoardDocument> roomOnBoardDocument = roomsOrHouseRepository.findById(roomOnBoardId);
                    if (roomOnBoardDocument.isPresent()) {
                        Set<Room> retrievedRooms = roomOnBoardDocument.get().getRooms();
                        // Handle null rooms safely
                        if (retrievedRooms != null && !retrievedRooms.isEmpty()) {
                            // Filter out rooms with null enum values or keep them as-is
                            // The custom deserializers will handle null values gracefully
                            roomNumbers.addAll(retrievedRooms);
                        }
                    }
                }

                coliveNameAndRooms.setRooms(roomNumbers);
                coliveNameAndRooms.setLicenseDocumentsPath(clientAndRoomOnBoardId.getLicenseDocumentsPath());
                coliveNameAndRooms.setUploadedPhotos(clientAndRoomOnBoardId.getUploadedPhotos());
                String category = clientAndRoomOnBoardId.getClientCategory();
                if(category == null || category.trim().isEmpty()) {
                    coliveNameAndRooms.setCategoryType(null);
                } else {
                    coliveNameAndRooms.setCategoryType(ColiveNameAndRooms.categoryValues.valueOf(category));}
                coliveNameAndRoomsSet.add(coliveNameAndRooms);
            }
        }
        response.setClientNameAndRooms(coliveNameAndRoomsSet);
        return response;
    }
}
