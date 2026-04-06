package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.document.RoomOnBoardDocument;
import com.elite4.anandan.registrationservices.dto.ClientAndRoomOnBoardId;
import com.elite4.anandan.registrationservices.dto.UserResponse;
import com.elite4.anandan.registrationservices.dto.ColiveListItem;
import com.elite4.anandan.registrationservices.dto.ColiveNameAndRooms;
import com.elite4.anandan.registrationservices.dto.Room;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.RoleRepository;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import com.elite4.anandan.registrationservices.repository.RoomsOrHouseRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
                coliveNameAndRooms.setBankDetails(clientDetail.getBankDetails());
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
        for (User u : users) {
            if (u.getUsername() != null && u.getUsername().toLowerCase().contains(search.toLowerCase())) {
                ColiveListItem item = new ColiveListItem();
                item.setColiveUserName(u.getUsername());
                item.setColiveName(u.getUsername());
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
}
