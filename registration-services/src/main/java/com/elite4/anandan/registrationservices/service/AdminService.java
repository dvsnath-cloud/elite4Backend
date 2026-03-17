package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.document.RoomOnBoardDocument;
import com.elite4.anandan.registrationservices.dto.ClientAndRoomOnBoardId;
import com.elite4.anandan.registrationservices.dto.ClientNameAndRooms;
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
            Set<ClientNameAndRooms> clientNameAndRoomsSet = new HashSet<>();
            Set<ClientAndRoomOnBoardId> clientAndRoomOnBoardIds = user.getClientDetails();
            if (clientAndRoomOnBoardIds != null) {
                for(ClientAndRoomOnBoardId clientAndRoomOnBoardId : clientAndRoomOnBoardIds) {
                    ClientNameAndRooms clientNameAndRooms = new ClientNameAndRooms();
                    clientNameAndRooms.setClientName(clientAndRoomOnBoardId.getClientName());
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

                    clientNameAndRooms.setRooms(roomNumbers);
                    String category = clientAndRoomOnBoardId.getClientCategory();
                    clientNameAndRooms.setCategoryType(ClientNameAndRooms.categoryValues.valueOf(category));
                    clientNameAndRoomsSet.add(clientNameAndRooms);
                }
            }
            response.setClientNameAndRooms(clientNameAndRoomsSet);
            return response;
    }
}
