package com.elite4.anandan.registrationservices.service;


import com.elite4.anandan.registrationservices.document.RoomOnBoardDocument;
import com.elite4.anandan.registrationservices.dto.*;
import com.elite4.anandan.registrationservices.model.EmployeeRole;
import com.elite4.anandan.registrationservices.model.Role;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.RoleRepository;
import com.elite4.anandan.registrationservices.repository.RoomsOrHouseRepository;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import com.mongodb.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Core logic for creating users, including validation, password encoding,
 * role resolution and response mapping.
 */
@Service
public class UserCreationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final PhoneService phoneService;
    private final RoomsOrHouseRepository roomsOrHouseRepository;

    public UserCreationService(UserRepository userRepository,
                               PasswordEncoder passwordEncoder, RoleRepository roleRepository, PhoneService phoneService, RoomsOrHouseRepository roomsOrHouseRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.phoneService = phoneService;
        this.roomsOrHouseRepository = roomsOrHouseRepository;
    }

    /**
     * Creates a new user based on the incoming request.
     *
     * @param request user creation payload
     * @return 201 with created user data, or 400 if username/email/roles are invalid
     */
    public ResponseEntity<?> createUser(UserCreateRequest request) {
        Set<ClientAndRoomOnBoardId> clientAndRoomOnBoardIdsSet = new HashSet<>();

        // Check if username/email/phone combination already exists with overlapping or exact client names
        if (request.getClientDetails() != null && !request.getClientDetails().isEmpty()) {
            // Extract client names from request
            Set<String> requestClientNames = request.getClientDetails().stream()
                    .map(ClientNameAndRooms::getClientName)
                    .collect(java.util.stream.Collectors.toSet());

            // Check if username already exists with overlapping client names
            Optional<User> existingUserByUsername = userRepository.findByUsername(request.getUsername());
            if (existingUserByUsername.isPresent()) {
                Set<String> existingClientNames = existingUserByUsername.get().getClientDetails().stream()
                        .map(ClientAndRoomOnBoardId::getClientName)
                        .collect(java.util.stream.Collectors.toSet());

                // Check for exact match
                if (existingClientNames.equals(requestClientNames)) {
                    return ResponseEntity.badRequest()
                            .body("Username '" + request.getUsername() + "' is already onboarded with the exact same set of client names: " + requestClientNames);
                }

                // Check for partial overlap (intersection)
                Set<String> overlap = new HashSet<>(existingClientNames);
                overlap.retainAll(requestClientNames);
                if (!overlap.isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body("Username '" + request.getUsername() + "' is already onboarded with overlapping client names: " + overlap);
                }
            }

            // Check if email already exists with overlapping client names
            if (request.getEmail() != null) {
                Optional<User> existingUserByEmail = userRepository.findByEmail(request.getEmail());
                if (existingUserByEmail.isPresent()) {
                    Set<String> existingClientNames = existingUserByEmail.get().getClientDetails().stream()
                            .map(ClientAndRoomOnBoardId::getClientName)
                            .collect(java.util.stream.Collectors.toSet());

                    // Check for exact match
                    if (existingClientNames.equals(requestClientNames)) {
                        return ResponseEntity.badRequest()
                                .body("Email '" + request.getEmail() + "' is already onboarded with the exact same set of client names: " + requestClientNames);
                    }

                    // Check for partial overlap (intersection)
                    Set<String> overlap = new HashSet<>(existingClientNames);
                    overlap.retainAll(requestClientNames);
                    if (!overlap.isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body("Email '" + request.getEmail() + "' is already onboarded with overlapping client names: " + overlap);
                    }
                }
            }

            // Check if phone number already exists with overlapping client names
            if (request.getPhoneNumber() != null) {
                try{
                    String e164 = phoneService.toE164(request.getPhoneNumber());
                    if(!e164.isBlank()) request.setPhoneNumber(e164);
                    else return ResponseEntity.badRequest()
                            .body("Invalid phone number format: " + request.getPhoneNumber());
                }catch (IllegalArgumentException e){
                    return ResponseEntity.badRequest()
                            .body("Invalid phone number format: " + request.getPhoneNumber());
                }
                Optional<User> existingUserByPhone = userRepository.findByPhoneE164(request.getPhoneNumber());
                if (existingUserByPhone.isPresent() && (!existingUserByPhone.isEmpty())) {
                    Set<String> existingClientNames = existingUserByPhone.get().getClientDetails().stream()
                            .map(ClientAndRoomOnBoardId::getClientName)
                            .collect(java.util.stream.Collectors.toSet());

                    // Check for exact match
                    if (existingClientNames.equals(requestClientNames)) {
                        return ResponseEntity.badRequest()
                                .body("Phone number '" + request.getPhoneNumber() + "' is already onboarded with the exact same set of client names: " + requestClientNames);
                    }

                    // Check for partial overlap (intersection)
                    Set<String> overlap = new HashSet<>(existingClientNames);
                    overlap.retainAll(requestClientNames);
                    if (!overlap.isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body("Phone number '" + request.getPhoneNumber() + "' is already onboarded with overlapping client names: " + overlap);
                    }
                }
            }

            for (ClientNameAndRooms clientName : request.getClientDetails()) {
                ClientAndRoomOnBoardId clientAndRoomOnBoardId = new ClientAndRoomOnBoardId();
                if (clientName.getRooms() != null && !clientName.getRooms().isEmpty()) {
                    for(Room room : clientName.getRooms()) {
                        // Validate that only one of room number or house number is provided
                        if ((room.getRoomNumber() != null && !room.getRoomNumber().trim().isEmpty()) &&
                                (room.getHouseNumber() != null && !room.getHouseNumber().trim().isEmpty())) {
                            return ResponseEntity
                                    .status(HttpStatus.BAD_REQUEST)
                                    .body("Both room number and house number cannot be provided for the same room");
                        } else if ((room.getRoomNumber() == null || room.getRoomNumber().trim().isEmpty()) &&
                                (room.getHouseNumber() == null || room.getHouseNumber().trim().isEmpty())) {
                            return ResponseEntity
                                    .status(HttpStatus.BAD_REQUEST)
                                    .body("Either room number or house number must be provided for each room");
                        }
                    }
                    RoomOnBoardDocument roomOnBoardDocument = new RoomOnBoardDocument();
                    roomOnBoardDocument.setRooms(clientName.getRooms());
                    roomsOrHouseRepository.save(roomOnBoardDocument);
                    clientAndRoomOnBoardId.setRoomOnBoardId(roomOnBoardDocument.getId());
                } else{

                }
                clientAndRoomOnBoardId.setClientName(clientName.getClientName());
                clientAndRoomOnBoardId.setClientCategory(String.valueOf(clientName.getCategoryType()));
                clientAndRoomOnBoardIdsSet.add(clientAndRoomOnBoardId);
            }
        }
        Set<String> validatedRoleIds = validateAndResolveRoleIds(request.getRoleIds());
        if (validatedRoleIds == null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Invalid role(s). Valid roles: ROLE_USER, ROLE_MODERATOR, ROLE_ADMIN");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setOwnerOfClient(request.getOwnerOfClient());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRoleIds(validatedRoleIds);
        user.setPhoneE164(phoneService.toE164(request.getPhoneNumber()));
        user.setPhoneRaw(request.getPhoneNumber());
        user.setClientDetails(clientAndRoomOnBoardIdsSet);
        User saved = userRepository.save(user);
        UserResponse response = toUserResponse(saved);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    public ResponseEntity<?> addClientToExistUser(AddClientToUser request) {
        Set<ClientAndRoomOnBoardId> clientAndRoomOnBoardIdsSet = new HashSet<>();
        Optional<User> existingUserByUsername = userRepository.findByUsername(request.getUsername());
        if (existingUserByUsername.isPresent()) {
            Set<String> existingClientNames = existingUserByUsername.get().getClientDetails().stream()
                    .map(ClientAndRoomOnBoardId::getClientName)
                    .collect(java.util.stream.Collectors.toSet());
            // Check for exact match
            if (existingClientNames.contains(request.getClientName())) {
                return ResponseEntity.badRequest()
                        .body("Username '" + request.getUsername() + "' is already onboarded with the exact same set of client names: " + request.getClientName());
            }
        } else {
            return ResponseEntity.badRequest().body("User with username '" + request.getUsername() + "' not found");
        }
        if (request.getEmail() != null) {
            Optional<User> existingUserByEmail = userRepository.findByEmail(request.getEmail());
            if (existingUserByEmail.isPresent()) {
                Set<String> existingClientNames = existingUserByEmail.get().getClientDetails().stream()
                        .map(ClientAndRoomOnBoardId::getClientName)
                        .collect(java.util.stream.Collectors.toSet());

                // Check for exact match
                if (existingClientNames.contains(request.getClientName())) {
                    return ResponseEntity.badRequest()
                            .body("Email '" + request.getEmail() + "' is already onboarded with the exact same set of client names: " + request.getClientName());
                }
            }
        }
        // Check if phone number already exists with overlapping client names
        if (request.getPhoneNumber() != null) {
            try{
                String e164 = phoneService.toE164(request.getPhoneNumber());
                if(!e164.isBlank()) request.setPhoneNumber(e164);
                else return ResponseEntity.badRequest()
                        .body("Invalid phone number format: " + request.getPhoneNumber());
            }catch (Exception e){
                return ResponseEntity.badRequest()
                        .body("Invalid phone number format: " + request.getPhoneNumber());
            }
            Optional<User> existingUserByPhone = userRepository.findByPhoneE164(request.getPhoneNumber());
            if (existingUserByPhone.isPresent() && (!existingUserByPhone.isEmpty())) {
                Set<String> existingClientNames = existingUserByPhone.get().getClientDetails().stream()
                        .map(ClientAndRoomOnBoardId::getClientName)
                        .collect(java.util.stream.Collectors.toSet());

                // Check for exact match
                if (existingClientNames.contains(request.getClientName())) {
                    return ResponseEntity.badRequest()
                            .body("Phone number '" + request.getPhoneNumber() + "' is already onboarded with the exact same set of client names: " + request.getClientName());
                }
            }
        }

        ClientAndRoomOnBoardId clientAndRoomOnBoardId = new ClientAndRoomOnBoardId();
        if (request.getRooms() != null && !request.getRooms().isEmpty()) {
            for(Room room : request.getRooms()) {
                // Validate that only one of room number or house number is provided
                if ((room.getRoomNumber() != null && !room.getRoomNumber().trim().isEmpty()) &&
                        (room.getHouseNumber() != null && !room.getHouseNumber().trim().isEmpty())) {
                    return ResponseEntity
                            .status(HttpStatus.BAD_REQUEST)
                            .body("Both room number and house number cannot be provided for the same room");
                } else if ((room.getRoomNumber() == null || room.getRoomNumber().trim().isEmpty()) &&
                        (room.getHouseNumber() == null || room.getHouseNumber().trim().isEmpty())) {
                    return ResponseEntity
                            .status(HttpStatus.BAD_REQUEST)
                            .body("Either room number or house number must be provided for each room");
                }
            }
            RoomOnBoardDocument roomOnBoardDocument = new RoomOnBoardDocument();
            roomOnBoardDocument.setRooms(request.getRooms());
            roomsOrHouseRepository.save(roomOnBoardDocument);
            clientAndRoomOnBoardId.setRoomOnBoardId(roomOnBoardDocument.getId());
        }
        clientAndRoomOnBoardId.setClientName(request.getClientName());
        clientAndRoomOnBoardId.setClientCategory(String.valueOf(request.getCategoryType()));
        clientAndRoomOnBoardIdsSet.add(clientAndRoomOnBoardId);

        // Find user and add client
        return userRepository.findByUsername(request.getUsername())
                .map(u -> {
                    Set<ClientAndRoomOnBoardId> existingClients = u.getClientDetails();
                    if (existingClients == null) {
                        existingClients = new HashSet<>();
                    }

                    // Add new client to existing set
                    existingClients.addAll(clientAndRoomOnBoardIdsSet);
                    u.setClientDetails(existingClients);

                    // Update other fields if provided
                    if (request.getEmail() != null) {
                        u.setEmail(request.getEmail());
                    }
                    if (request.getPhoneNumber() != null) {
                        u.setPhoneE164(phoneService.toE164(request.getPhoneNumber()));
                        u.setPhoneRaw(request.getPhoneNumber());
                    }

                    User saved = userRepository.save(u);
                    return ResponseEntity.ok(toUserResponse(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }


    public ResponseEntity<?> updateRoomType(UpdateRoomType request) {
        // Find the user by username
        return userRepository.findByUsername(request.getUsername())
                .map(user -> {
                    Set<ClientAndRoomOnBoardId> clientNames = user.getClientDetails();
                    if (clientNames == null || clientNames.isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body("User does not have any clients assigned");
                    }

                    // Find the client with the specified client name
                    ClientAndRoomOnBoardId targetClient = null;
                    for (ClientAndRoomOnBoardId client : clientNames) {
                        if (request.getClientName().equals(client.getClientName())) {
                            targetClient = client;
                            break;
                        }
                    }

                    if (targetClient == null) {
                        return ResponseEntity.badRequest()
                                .body("User does not have client '" + request.getClientName() + "' assigned");
                    }

                    // Check if the client has a room on board ID
                    if (targetClient.getRoomOnBoardId() == null) {
                        return ResponseEntity.badRequest()
                                .body("Client '" + request.getClientName() + "' does not have any rooms assigned");
                    }

                    // Find the RoomOnBoardDocument
                    Optional<RoomOnBoardDocument> roomOnBoardDocOpt = roomsOrHouseRepository.findById(targetClient.getRoomOnBoardId());
                    if (roomOnBoardDocOpt.isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body("Room data not found for client '" + request.getClientName() + "'");
                    }

                    RoomOnBoardDocument roomOnBoardDoc = roomOnBoardDocOpt.get();
                    Set<Room> rooms = roomOnBoardDoc.getRooms();

                    if (rooms == null || rooms.isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body("No rooms found for client '" + request.getClientName() + "'");
                    }

                    // Find and update the specific room
                    boolean roomFound = false;
                    for (Room room : rooms) {
                        if (request.getRoomNumber().equals(room.getRoomNumber())) {
                            room.setRoomType(request.getRoomType());
                            roomFound = true;
                            break;
                        }
                    }

                    if (!roomFound) {
                        return ResponseEntity.badRequest()
                                .body("Room number '" + request.getRoomNumber() + "' not found for client '" + request.getClientName() + "'");
                    }

                    // Save the updated RoomOnBoardDocument
                    roomsOrHouseRepository.save(roomOnBoardDoc);

                    // Update user timestamp
                    user.setUpdatedAt(java.time.Instant.now());

                    // Save the updated user
                    User savedUser = userRepository.save(user);

                    return ResponseEntity.ok(toUserResponse(savedUser));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Validates role values against the {@link EmployeeRole} enum.
     * <p>
     * This method validates only the values explicitly provided in the request.
     * No default roles are added implicitly and raw MongoDB IDs are not accepted
     * anymore – the client must send role names such as ROLE_USER, ROLE_ADMIN, etc.
     *
     * @param roleValues role names from the request
     * @return set of valid role names to store on User, or null if any role is invalid
     */
    private Set<String> validateAndResolveRoleIds(Set<String> roleValues) {
        if (roleValues == null || roleValues.isEmpty()) {
            return new HashSet<>();
        }

        Set<String> resolvedRoles = new HashSet<>();
        for (String value : roleValues) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                // Only allow values that match a defined EmployeeRole.
                EmployeeRole employeeRole = EmployeeRole.valueOf(value.trim().toUpperCase());
                Role role = new Role();
                role.setName(employeeRole);
                Role saved = roleRepository.save(role);
                resolvedRoles.add(saved.getId());
            } catch (IllegalArgumentException e) {
                // If the provided value does not map to a known EmployeeRole,
                // treat it as invalid instead of trying to interpret it as a raw ID.
                return null;
            }
        }
        return resolvedRoles;
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


    public Optional<User> findByPhone(String rawPhone) {
        String e164 = phoneService.toE164(rawPhone);
        if (e164 == null) return Optional.empty();
        return userRepository.findByPhoneE164(e164);
    }


    public boolean existsPhoneNumberWithRawMatch(String rawPhone) {
        String e164 = phoneService.toE164(rawPhone);
        if (e164 == null) {
            return false;
        }
        return userRepository.findByPhoneE164(e164)
                .map(u -> rawPhone != null && rawPhone.equals(u.getPhoneRaw()))
                .orElse(false);
    }


    public Optional<User> findByUserName(String userName) {
        return userRepository.findByUsername(userName);
    }

    @Transactional
    public User upsertByPhone(String rawPhone, String name, String passwordHash, String email) {
        String e164 = phoneService.toE164(rawPhone);
        if (e164 == null) {
            throw new IllegalArgumentException("Invalid phone number");
        }
        return userRepository.findByPhoneE164(e164)
                .map(u -> {
                    // Update existing as needed
                    u.setPhoneRaw(rawPhone);
                    if (name != null && !name.isBlank()) u.setUsername(name);
                    return userRepository.save(u);
                })
                .orElseGet(() -> {
                    try {
                        return userRepository.save(new User(name, passwordHash, email, e164, rawPhone));
                    } catch (DuplicateKeyException e) {
                        // Rare race condition: someone inserted concurrently
                        return userRepository.findByPhoneE164(e164).orElseThrow();
                    }
                });
    }

}

