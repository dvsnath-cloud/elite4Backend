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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Core logic for creating users, including validation, password encoding,
 * role resolution and response mapping.
 */
@Service
@Slf4j
public class UserCreationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final PhoneService phoneService;
    private final RoomsOrHouseRepository roomsOrHouseRepository;
    private final FileStorageService fileStorageService;

    public UserCreationService(UserRepository userRepository,
                               PasswordEncoder passwordEncoder, RoleRepository roleRepository, PhoneService phoneService, RoomsOrHouseRepository roomsOrHouseRepository, FileStorageService fileStorageService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.phoneService = phoneService;
        this.roomsOrHouseRepository = roomsOrHouseRepository;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Creates a new user based on the incoming request.
     *
     * @param request user creation payload
     * @return 201 with created user data, or 400 if username/email/roles are invalid
     */
    @SuppressWarnings("rawtypes")
    public ResponseEntity createUser(UserCreateRequest request) {
        Set<ClientAndRoomOnBoardId> clientAndRoomOnBoardIdsSet = new HashSet<>();

        try {
            log.info("🔍 VALIDATING SIGNUP - Username: {}, Email: {}, Phone: {}",
                    request.getUsername(), request.getEmail(), request.getPhoneNumber());

            // ============================================
            // CHECK 1: Username must be unique
            // ============================================
            if (userRepository.findByUsername(request.getUsername()).isPresent()) {
                log.warn("⚠️ SIGNUP VALIDATION FAILED - Username already exists: {}", request.getUsername());
                return ResponseEntity.badRequest()
                        .body(ErrorResponse.badRequest("USERNAME_ALREADY_EXISTS",
                                "Username '" + request.getUsername() + "' is already taken"));
            }

            // ============================================
            // CHECK 2: Email must be unique (if provided)
            // ============================================
            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                    log.warn("⚠️ SIGNUP VALIDATION FAILED - Email already exists: {}", request.getEmail());
                    return ResponseEntity.badRequest()
                            .body(ErrorResponse.badRequest("EMAIL_ALREADY_EXISTS",
                                    "Email '" + request.getEmail() + "' is already registered"));
                }
            }

            // ============================================
            // CHECK 3: Phone number must be unique (if provided)
            // ============================================
            if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
                try {
                    String e164 = phoneService.toE164(request.getPhoneNumber());
                    if (e164 != null && !e164.isBlank()) {
                        request.setPhoneNumber(e164);

                        if (userRepository.findByPhoneE164(e164).isPresent()) {
                            log.warn("⚠️ SIGNUP VALIDATION FAILED - Phone number already exists: {}", e164);
                            return ResponseEntity.badRequest()
                                    .body(ErrorResponse.badRequest("PHONE_ALREADY_EXISTS",
                                            "Phone number '" + request.getPhoneNumber() + "' is already registered"));
                        }
                    } else {
                        log.warn("⚠️ SIGNUP VALIDATION FAILED - Invalid phone number format: {}", request.getPhoneNumber());
                        return ResponseEntity.badRequest()
                                .body(ErrorResponse.badRequest("INVALID_PHONE_FORMAT",
                                        "Invalid phone number format: " + request.getPhoneNumber()));
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("⚠️ SIGNUP VALIDATION FAILED - Invalid phone number: {}", request.getPhoneNumber());
                    return ResponseEntity.badRequest()
                            .body(ErrorResponse.badRequest("INVALID_PHONE_FORMAT",
                                    "Invalid phone number format: " + request.getPhoneNumber()));
                }
            }

            log.info("✅ ALL VALIDATION PASSED - Username: {}, Email: {}, Phone: {}",
                    request.getUsername(), request.getEmail(), request.getPhoneNumber());

            // ============================================
            // PROCESSING: Create user with valid data
            // ============================================
            if (request.getColiveDetails() != null && !request.getColiveDetails().isEmpty()) {
                for (ColiveNameAndRooms clientName : request.getColiveDetails()) {
                    ClientAndRoomOnBoardId clientAndRoomOnBoardId = new ClientAndRoomOnBoardId();
                    if (clientName.getRooms() != null && !clientName.getRooms().isEmpty()) {
                        for (Room room : clientName.getRooms()) {
                            // Validate that only one of room number or house number is provided
                            if ((room.getRoomNumber() != null && !room.getRoomNumber().trim().isEmpty()) &&
                                    (room.getHouseNumber() != null && !room.getHouseNumber().trim().isEmpty())) {
                                return ResponseEntity
                                        .status(HttpStatus.BAD_REQUEST)
                                        .body(ErrorResponse.badRequest("INVALID_ROOM_DATA",
                                                "Both room number and house number cannot be provided for the same room"));
                            } else if ((room.getRoomNumber() == null || room.getRoomNumber().trim().isEmpty()) &&
                                    (room.getHouseNumber() == null || room.getHouseNumber().trim().isEmpty())) {
                                return ResponseEntity
                                        .status(HttpStatus.BAD_REQUEST)
                                        .body(ErrorResponse.badRequest("INVALID_ROOM_DATA",
                                                "Either room number or house number must be provided for each room"));
                            }
                        }
                        RoomOnBoardDocument roomOnBoardDocument = new RoomOnBoardDocument();
                        roomOnBoardDocument.setRooms(clientName.getRooms());
                        roomsOrHouseRepository.save(roomOnBoardDocument);
                        clientAndRoomOnBoardId.setRoomOnBoardId(roomOnBoardDocument.getId());
                    } else {

                    }
                    clientAndRoomOnBoardId.setColiveName(clientName.getColiveName());
                    clientAndRoomOnBoardId.setClientCategory(String.valueOf(clientName.getCategoryType()));
                    clientAndRoomOnBoardId.setBankDetails(clientName.getBankDetails());

                    // Set file paths from ColiveNameAndRooms
                    if (clientName.getAadharPhotoPath() != null && !clientName.getAadharPhotoPath().isBlank()) {
                        clientAndRoomOnBoardId.setAadharPhotoPath(clientName.getAadharPhotoPath());
                        log.info("Aadhar photo path set for client: {}", clientName.getColiveName());
                    }
                    if (clientName.getDocumentUploadPath() != null && !clientName.getDocumentUploadPath().isBlank()) {
                        clientAndRoomOnBoardId.setDocumentUploadPath(clientName.getDocumentUploadPath());
                        clientAndRoomOnBoardId.setDocumentType(clientName.getDocumentType());
                        clientAndRoomOnBoardId.setDocumentNumber(clientName.getDocumentNumber());
                        log.info("Document path set for client: {}", clientName.getColiveName());
                    }

                    clientAndRoomOnBoardIdsSet.add(clientAndRoomOnBoardId);
                }
            }
            Set<String> validatedRoleIds = validateAndResolveRoleIds(request.getRoleIds());
            if (validatedRoleIds == null) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(ErrorResponse.badRequest("INVALID_ROLES",
                                "Invalid role(s). Valid roles: ROLE_USER, ROLE_MODERATOR, ROLE_ADMIN"));
            }
            User user = new User();
            user.setUsername(request.getUsername());
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            user.setEmail(request.getEmail());
            user.setRoleIds(validatedRoleIds);
            user.setPhoneE164(phoneService.toE164(request.getPhoneNumber()));
            user.setPhoneRaw(request.getPhoneNumber());
            user.setClientDetails(clientAndRoomOnBoardIdsSet);
            if (request.getRoleIds().contains("ROLE_USER")) {
                user.setActive(true);
            }
            try {
                User saved = userRepository.save(user);
                UserResponse response = toUserResponse(saved);
                log.info("✅ SIGNUP SUCCESS - User created: Username: {}, Email: {}, Phone: {}",
                        saved.getUsername(), saved.getEmail(), saved.getPhoneE164());
                return ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(response);
            } catch (DuplicateKeyException e) {
                log.error("❌ SIGNUP ERROR - Duplicate key error: {}", e.getMessage());
                return ResponseEntity.badRequest()
                        .body("User already exists in database");
            } catch (Exception e) {
                log.error("❌ SIGNUP ERROR - Exception: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Signup failed: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("❌ SIGNUP VALIDATION ERROR - Exception: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Signup failed during validation: " + e.getMessage());
        }
    }

    @SuppressWarnings("rawtypes")
    public ResponseEntity addClientToExistUser(AddClientToUser request) {
        Set<ClientAndRoomOnBoardId> clientAndRoomOnBoardIdsSet = new HashSet<>();
        Optional<User> existingUserByUsername = userRepository.findByUsername(request.getUsername());
        if (existingUserByUsername.isPresent()) {
            Set<String> existingClientNames = existingUserByUsername.get().getClientDetails().stream()
                    .map(ClientAndRoomOnBoardId::getColiveName)
                    .collect(java.util.stream.Collectors.toSet());
            // Check for exact match
            if (existingClientNames.contains(request.getColiveName())) {
                return ResponseEntity.badRequest()
                        .body(ErrorResponse.badRequest("CLIENT_ALREADY_EXISTS",
                                "Username '" + request.getUsername() + "' is already onboarded with client name: " + request.getColiveName()));
            }
        } else {
            return ResponseEntity.badRequest().body(ErrorResponse.badRequest("USER_NOT_FOUND",
                    "User with username '" + request.getUsername() + "' not found"));
        }
        if (request.getEmail() != null) {
            Optional<User> existingUserByEmail = userRepository.findByEmail(request.getEmail());
            if (existingUserByEmail.isPresent()) {
                Set<String> existingClientNames = existingUserByEmail.get().getClientDetails().stream()
                        .map(ClientAndRoomOnBoardId::getColiveName)
                        .collect(java.util.stream.Collectors.toSet());

                // Check for exact match
                if (existingClientNames.contains(request.getColiveName())) {
                    return ResponseEntity.badRequest()
                            .body(ErrorResponse.badRequest("CLIENT_ALREADY_EXISTS",
                                    "Email '" + request.getEmail() + "' is already onboarded with client name: " + request.getColiveName()));
                }
            }
        }
        // Check if phone number already exists
        if (request.getPhoneNumber() != null) {
            try{
                String e164 = phoneService.toE164(request.getPhoneNumber());
                if(e164 != null && !e164.isBlank()) {
                    request.setPhoneNumber(e164);
                    Optional<User> existingUserByPhone = userRepository.findByPhoneE164(e164);
                    if (existingUserByPhone.isPresent()) {
                        Set<String> existingClientNames = existingUserByPhone.get().getClientDetails().stream()
                                .map(ClientAndRoomOnBoardId::getColiveName)
                                .collect(java.util.stream.Collectors.toSet());

                        // Check for exact match
                        if (existingClientNames.contains(request.getColiveName())) {
                            return ResponseEntity.badRequest()
                                    .body(ErrorResponse.badRequest("CLIENT_ALREADY_EXISTS",
                                            "Phone number '" + request.getPhoneNumber() + "' is already onboarded with client name: " + request.getColiveName()));
                        }
                    }
                } else {
                    return ResponseEntity.badRequest()
                            .body(ErrorResponse.badRequest("INVALID_PHONE_FORMAT",
                                    "Invalid phone number format: " + request.getPhoneNumber()));
                }
            }catch (Exception e){
                return ResponseEntity.badRequest()
                        .body(ErrorResponse.badRequest("INVALID_PHONE_FORMAT",
                                "Invalid phone number format: " + request.getPhoneNumber()));
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
                            .body(ErrorResponse.badRequest("INVALID_ROOM_DATA",
                                    "Both room number and house number cannot be provided for the same room"));
                } else if ((room.getRoomNumber() == null || room.getRoomNumber().trim().isEmpty()) &&
                        (room.getHouseNumber() == null || room.getHouseNumber().trim().isEmpty())) {
                    return ResponseEntity
                            .status(HttpStatus.BAD_REQUEST)
                            .body(ErrorResponse.badRequest("INVALID_ROOM_DATA",
                                    "Either room number or house number must be provided for each room"));
                }
            }
            RoomOnBoardDocument roomOnBoardDocument = new RoomOnBoardDocument();
            roomOnBoardDocument.setRooms(request.getRooms());
            roomsOrHouseRepository.save(roomOnBoardDocument);
            clientAndRoomOnBoardId.setRoomOnBoardId(roomOnBoardDocument.getId());
        }
        clientAndRoomOnBoardId.setColiveName(request.getColiveName());
        clientAndRoomOnBoardId.setClientCategory(String.valueOf(request.getCategoryType()));
        clientAndRoomOnBoardId.setBankDetails(request.getBankDetails());

        // Set file attributes from AddClientToUser request
        if (request.getAadharPhotoPath() != null && !request.getAadharPhotoPath().isBlank()) {
            clientAndRoomOnBoardId.setAadharPhotoPath(request.getAadharPhotoPath());
            log.info("Aadhar photo path set for client: {}", request.getColiveName());
        }
        if (request.getDocumentUploadPath() != null && !request.getDocumentUploadPath().isBlank()) {
            clientAndRoomOnBoardId.setDocumentUploadPath(request.getDocumentUploadPath());
            if (request.getDocumentType() != null) {
                clientAndRoomOnBoardId.setDocumentType(request.getDocumentType());
            }
            if (request.getDocumentNumber() != null) {
                clientAndRoomOnBoardId.setDocumentNumber(request.getDocumentNumber());
            }
            log.info("Document path set for client: {}", request.getColiveName());
        }

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

    /**
     * Add client to existing user with file uploads (aadhar photo and document)
     * Handles AddClientToUser file attributes:
     * - aadharPhotoPath: Aadhar photo for the client
     * - documentUploadPath: Document (like registration certificate)
     * - documentType: Type of document
     * - documentNumber: Document number
     *
     * Uses FileStorageService methods: uploadFile, generateFileName
     *
     * @param request add client to user payload
     * @param clientFilesMap Map of fileType -> fileBytes
     *        Example: {"aadhar" -> bytes, "document" -> bytes}
     * @return 200 with updated user data including file paths
     */
    @SuppressWarnings("rawtypes")
    public ResponseEntity addClientToUserWithFiles(AddClientToUser request,
                                                    Map<String, byte[]> clientFilesMap) {
        log.info("Starting add client with file upload for user: {}", request.getUsername());

        // Step 1: Get the user ID first
        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());
        if (!userOpt.isPresent()) {
            return ResponseEntity.badRequest().body("User with username '" + request.getUsername() + "' not found");
        }

        String userId = userOpt.get().getId();
        String coLiveName = request.getColiveName();

        // Step 2: Upload files if provided
        if (clientFilesMap != null && !clientFilesMap.isEmpty()) {
            // Upload aadhar photo for client
            if (clientFilesMap.containsKey("aadhar") && clientFilesMap.get("aadhar") != null && clientFilesMap.get("aadhar").length > 0) {
                try {
                    byte[] aadharBytes = clientFilesMap.get("aadhar");
                    String fileName = fileStorageService.generateFileName(userId + "_" + coLiveName, "aadhar.jpg", "client_aadhar");
                    log.info("Generated aadhar filename: {}", fileName);

                    String aadharPath = fileStorageService.uploadFile(fileName, aadharBytes, "image/jpeg");
                    log.info("Aadhar photo uploaded for client {} at: {}", coLiveName, aadharPath);

                    request.setAadharPhotoPath(aadharPath);
                } catch (Exception e) {
                    log.error("Failed to upload aadhar for client {}: {}", coLiveName, e.getMessage(), e);
                }
            }

            // Upload document for client
            if (clientFilesMap.containsKey("document") && clientFilesMap.get("document") != null && clientFilesMap.get("document").length > 0) {
                try {
                    byte[] docBytes = clientFilesMap.get("document");
                    String fileName = fileStorageService.generateFileName(userId + "_" + coLiveName, "document.pdf", "client_document");
                    log.info("Generated document filename: {}", fileName);

                    String docPath = fileStorageService.uploadFile(fileName, docBytes, "application/pdf");
                    log.info("Document uploaded for client {} at: {}", coLiveName, docPath);

                    request.setDocumentUploadPath(docPath);
                } catch (Exception e) {
                    log.error("Failed to upload document for client {}: {}", coLiveName, e.getMessage(), e);
                }
            }
        }

        // Step 3: Add client with file paths (if set)
        ResponseEntity<?> addClientResponse = addClientToExistUser(request);

        log.info("Add client with files completed for userId: {}, clientName: {}", userId, coLiveName);
        return addClientResponse;
    }


    @SuppressWarnings("rawtypes")
    public ResponseEntity updateRoomType(UpdateRoomType request) {
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
                        if (request.getClientName().equals(client.getColiveName())) {
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
     * Creates a new user with file uploads for each client
     * Handles ColiveNameAndRooms file attributes:
     * - aadharPhotoPath: Aadhar photo for the client
     * - documentUploadPath: Document (like registration certificate)
     * - documentType: Type of document
     * - documentNumber: Document number
     *
     * Uses FileStorageService methods: uploadFile, generateFileName, fileExists
     *
     * @param request user creation payload with coliveDetails containing file data
     * @param clientFilesMap Map of clientName -> Map of fileType -> fileBytes
     *        Example: {"elite4CoLive" -> {"aadhar" -> bytes, "document" -> bytes}}
     * @return 201 with created user data including file paths for each client
     */
    @SuppressWarnings("rawtypes")
    public ResponseEntity createUserWithClientFiles(UserCreateRequest request,
                                                     Map<String, Map<String, byte[]>> clientFilesMap) {
        log.info("Starting user creation with client files for username: {}", request.getUsername());

        // Step 1: Create user first (existing logic)
        ResponseEntity<?> createResponse = createUser(request);

        // If creation failed, return the error
        if (!createResponse.getStatusCode().is2xxSuccessful()) {
            log.warn("User creation failed, skipping file uploads");
            return createResponse;
        }

        // Step 2: Extract user response
        UserResponse userResponse = (UserResponse) createResponse.getBody();
        if (userResponse == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create user");
        }

        String userId = userResponse.getId();
        log.info("User created with ID: {}. Starting file uploads for clients", userId);

        // Step 3: Upload files for each client
        if (clientFilesMap != null && !clientFilesMap.isEmpty()) {
            for (Map.Entry<String, Map<String, byte[]>> clientEntry : clientFilesMap.entrySet()) {
                String coLiveName = clientEntry.getKey();
                Map<String, byte[]> fileMap = clientEntry.getValue();

                log.info("Processing files for client: {}", coLiveName);

                // Upload aadhar photo for client
                if (fileMap.containsKey("aadhar") && fileMap.get("aadhar") != null && fileMap.get("aadhar").length > 0) {
                    try {
                        byte[] aadharBytes = fileMap.get("aadhar");
                        String fileName = fileStorageService.generateFileName(userId + "_" + coLiveName, "aadhar.jpg", "client_aadhar");
                        log.info("Generated aadhar filename: {}", fileName);

                        String aadharPath = fileStorageService.uploadFile(fileName, aadharBytes, "image/jpeg");
                        log.info("Aadhar photo uploaded for client {} at: {}", coLiveName, aadharPath);

                        // Save path to client details
                        updateClientFilePath(userId, coLiveName, "aadhar", aadharPath, null, null);
                    } catch (Exception e) {
                        log.error("Failed to upload aadhar for client {}: {}", coLiveName, e.getMessage(), e);
                    }
                }

                // Upload document for client
                if (fileMap.containsKey("document") && fileMap.get("document") != null && fileMap.get("document").length > 0) {
                    try {
                        byte[] docBytes = fileMap.get("document");
                        String fileName = fileStorageService.generateFileName(userId + "_" + coLiveName, "document.pdf", "client_document");
                        log.info("Generated document filename: {}", fileName);

                        String docPath = fileStorageService.uploadFile(fileName, docBytes, "application/pdf");
                        log.info("Document uploaded for client {} at: {}", coLiveName, docPath);

                        // Get document type and number if available
                        String docType = fileMap.containsKey("documentType") ? new String(fileMap.get("documentType"), StandardCharsets.UTF_8) : null;
                        String docNumber = fileMap.containsKey("documentNumber") ? new String(fileMap.get("documentNumber"), StandardCharsets.UTF_8) : null;
                        // Save path to client details
                        updateClientFilePath(userId, coLiveName, "document", docPath, docType, docNumber);
                    } catch (Exception e) {
                        log.error("Failed to upload document for client {}: {}", coLiveName, e.getMessage(), e);
                    }
                }
            }
        }

        log.info("User creation with client files completed for userId: {}", userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(userResponse);
    }

    /**
     * Updates client file path in user's client details
     * Helper method for createUserWithClientFiles
     *
     * @param userId User ID
     * @param clientName Client name
     * @param fileType File type (aadhar or document)
     * @param filePath File path from storage
     * @param documentType Document type
     * @param documentNumber Document number
     */
    private void updateClientFilePath(String userId, String clientName, String fileType, String filePath,
                                     String documentType, String documentNumber) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Set<ClientAndRoomOnBoardId> clientDetails = user.getClientDetails();

                if (clientDetails != null) {
                    // Find the client and update file path
                    for (ClientAndRoomOnBoardId client : clientDetails) {
                        if (client.getColiveName().equals(clientName)) {
                            if ("aadhar".equalsIgnoreCase(fileType)) {
                                client.setAadharPhotoPath(filePath);
                                log.info("Aadhar photo path saved for client: {}", clientName);
                            } else if ("document".equalsIgnoreCase(fileType)) {
                                client.setDocumentUploadPath(filePath);
                                if (documentType != null) {
                                    client.setDocumentType(documentType);
                                }
                                if (documentNumber != null) {
                                    client.setDocumentNumber(documentNumber);
                                }
                                log.info("Document path saved for client: {}", clientName);
                            }
                            break;
                        }
                    }

                    // Save updated user
                    userRepository.save(user);
                    log.info("Client details updated and saved for client: {}", clientName);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to save {} file path for client {}: {}", fileType, clientName, e.getMessage());
        }
    }

    /**
     * Downloads client file (aadhar or document) for a specific client
     * Uses FileStorageService.downloadFile()
     *
     * @param userId User ID
     * @param clientName Client name
     * @param fileType File type (aadhar or document)
     * @return File content as byte array
     */
    public byte[] downloadClientFileForSignup(String userId, String clientName, String fileType) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Set<ClientAndRoomOnBoardId> clientDetails = user.getClientDetails();

                if (clientDetails != null) {
                    for (ClientAndRoomOnBoardId client : clientDetails) {
                        if (client.getColiveName().equals(clientName)) {
                            String filePath = "aadhar".equalsIgnoreCase(fileType)
                                ? client.getAadharPhotoPath()
                                : client.getDocumentUploadPath();

                            if (filePath == null || filePath.isBlank()) {
                                throw new IllegalArgumentException("No " + fileType + " found for client: " + clientName);
                            }

                            // downloadFile() - Retrieve from storage
                            byte[] content = fileStorageService.downloadFile(filePath);
                            log.info("Client {} file downloaded, type: {}, size: {} bytes", clientName, fileType, content.length);
                            return content;
                        }
                    }
                }
            }
            throw new IllegalArgumentException("Client not found: " + clientName);
        } catch (Exception e) {
            log.error("Failed to download {} for client {}: {}", fileType, clientName, e.getMessage());
            throw new RuntimeException("Failed to download file: " + e.getMessage());
        }
    }

    /**
     * Checks if client file exists
     * Uses FileStorageService.fileExists()
     *
     * @param userId User ID
     * @param clientName Client name
     * @param fileType File type (aadhar or document)
     * @return true if file exists, false otherwise
     */
    public boolean clientFileExistsForSignup(String userId, String clientName, String fileType) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Set<ClientAndRoomOnBoardId> clientDetails = user.getClientDetails();

                if (clientDetails != null) {
                    for (ClientAndRoomOnBoardId client : clientDetails) {
                        if (client.getColiveName().equals(clientName)) {
                            String filePath = "aadhar".equalsIgnoreCase(fileType)
                                ? client.getAadharPhotoPath()
                                : client.getDocumentUploadPath();

                            if (filePath == null || filePath.isBlank()) {
                                return false;
                            }

                            // fileExists() - Check in storage
                            boolean exists = fileStorageService.fileExists(filePath);
                            log.info("Client {} file existence check, type: {}, result: {}", clientName, fileType, exists);
                            return exists;
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Error checking file existence for client {}: {}", clientName, e.getMessage());
            return false;
        }
    }

    /**
     * Deletes client files for signup
     * Uses FileStorageService.deleteFile()
     *
     * @param userId User ID
     * @param clientName Client name
     */
    public void deleteClientFilesForSignup(String userId, String clientName) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Set<ClientAndRoomOnBoardId> clientDetails = user.getClientDetails();

                if (clientDetails != null) {
                    for (ClientAndRoomOnBoardId client : clientDetails) {
                        if (client.getColiveName().equals(clientName)) {
                            // Delete aadhar photo
                            if (client.getAadharPhotoPath() != null && !client.getAadharPhotoPath().isBlank()) {
                                try {
                                    if (fileStorageService.fileExists(client.getAadharPhotoPath())) {
                                        fileStorageService.deleteFile(client.getAadharPhotoPath());
                                        log.info("Client aadhar photo deleted for: {}", clientName);
                                    }
                                } catch (Exception e) {
                                    log.warn("Failed to delete aadhar for {}: {}", clientName, e.getMessage());
                                }
                            }

                            // Delete document
                            if (client.getDocumentUploadPath() != null && !client.getDocumentUploadPath().isBlank()) {
                                try {
                                    if (fileStorageService.fileExists(client.getDocumentUploadPath())) {
                                        fileStorageService.deleteFile(client.getDocumentUploadPath());
                                        log.info("Client document deleted for: {}", clientName);
                                    }
                                } catch (Exception e) {
                                    log.warn("Failed to delete document for {}: {}", clientName, e.getMessage());
                                }
                            }

                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to delete client files for {}: {}", clientName, e.getMessage());
        }
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
                String category = clientAndRoomOnBoardId.getClientCategory();
                coliveNameAndRooms.setCategoryType(ColiveNameAndRooms.categoryValues.valueOf(category));
                coliveNameAndRooms.setBankDetails(clientAndRoomOnBoardId.getBankDetails());
                coliveNameAndRoomsSet.add(coliveNameAndRooms);
            }
        }
        response.setClientNameAndRooms(coliveNameAndRoomsSet);
        return response;
    }


    public Optional<User> findByPhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return Optional.empty();
        }
        String e164 = phoneService.toE164(rawPhone);
        if (e164 == null || e164.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByPhoneE164(e164);
    }


    public boolean existsPhoneNumberWithRawMatch(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return false;
        }
        String e164 = phoneService.toE164(rawPhone);
        if (e164 == null || e164.isBlank()) {
            return false;
        }
        return userRepository.findByPhoneE164(e164)
                .map(u -> rawPhone.equals(u.getPhoneRaw()))
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



