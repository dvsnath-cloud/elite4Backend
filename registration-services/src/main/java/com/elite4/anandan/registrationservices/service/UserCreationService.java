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
import java.util.*;

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
    public ResponseEntity createUser(UserCreateRequest request,java.util.Map<String, java.util.List<byte[]>> propertyPhotosMap,java.util.Map<String, java.util.List<byte[]>> licenseDocumentsMap) {
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
                    //clientAndRoomOnBoardId.setUploadedPhotos();
                    clientAndRoomOnBoardId.setClientCategory(String.valueOf(clientName.getCategoryType()));
                    clientAndRoomOnBoardId.setBankDetails(clientName.getBankDetails());
                    // Step 3: Upload property photos for each coLive and save to DB
                    if (propertyPhotosMap != null && !propertyPhotosMap.isEmpty()) {
                        java.util.List<byte[]> photosList = propertyPhotosMap.get(clientName.getColiveName());
                        java.util.List<String> uploadedPaths = new java.util.ArrayList<>();
                        for (int i = 0; i < photosList.size(); i++) {
                            byte[] photoBytes = photosList.get(i);
                            if (photoBytes != null && photoBytes.length > 0) {
                                try {
                                    String fileName = fileStorageService.generateFileName(
                                            clientName.getColiveName() + "_photo_" + (i + 1),
                                            ".jpg",
                                            "property_photos");
                                    String photoPath = fileStorageService.uploadFile(fileName, photoBytes, "image/jpeg");
                                    uploadedPaths.add(photoPath);
                                    log.info("✅ Property photo {} uploaded for {}: {}", (i + 1), clientName.getColiveName(), photoPath);
                                } catch (Exception e) {
                                    log.error("❌ Failed to upload property photo {} for {}: {}", (i + 1), clientName.getColiveName(), e.getMessage(), e);
                                }
                            }
                        }
                        clientAndRoomOnBoardId.setUploadedPhotos(uploadedPaths);
                    }
                    if (licenseDocumentsMap != null && !licenseDocumentsMap.isEmpty()) {
                         java.util.List<byte[]> docsList = licenseDocumentsMap.get(clientName.getColiveName());;
                        java.util.List<String> licenseUploadedPaths = new java.util.ArrayList<>();
                        for (int i = 0; i < docsList.size(); i++) {
                            byte[] docBytes = docsList.get(i);
                            if (docBytes != null && docBytes.length > 0) {
                                try {
                                    String fileName = fileStorageService.generateFileName(
                                            clientName.getColiveName() + "_license_" + (i + 1),
                                            ".pdf",
                                            "property_license");
                                    String docPath = fileStorageService.uploadFile(fileName, docBytes, "application/pdf");
                                    licenseUploadedPaths.add(docPath);
                                    log.info("✅ Property photo {} uploaded for {}: {}", (i + 1), clientName.getColiveName(), docPath);
                                } catch (Exception e) {
                                    log.error("❌ Failed to upload property photo {} for {}: {}", (i + 1), clientName.getColiveName(), e.getMessage(), e);
                                }
                            }
                        }
                        clientAndRoomOnBoardId.setLicenseDocumentsPath(licenseUploadedPaths);
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
    public ResponseEntity addClientToExistUser(AddClientToUser request,List<byte[]> propertyPhotos, List<byte[]> licenseDocuments) {
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
        // Step 2: Upload property photos if provided
        java.util.List<String> uploadedPaths = new java.util.ArrayList<>();
        if (propertyPhotos != null && !propertyPhotos.isEmpty()) {
            for (int i = 0; i < propertyPhotos.size(); i++) {
                byte[] photoBytes = propertyPhotos.get(i);
                if (photoBytes != null && photoBytes.length > 0) {
                    try {
                        String fileName = fileStorageService.generateFileName(
                                 request.getColiveName() + "_property_" + (i + 1),
                                ".jpg",
                                "property_photos");
                        String photoPath = fileStorageService.uploadFile(fileName, photoBytes, "image/jpeg");
                        uploadedPaths.add(photoPath);
                        log.info("✅ Property photo {} uploaded for coLive {}: {}", (i + 1), request.getColiveName(), photoPath);
                    } catch (Exception e) {
                        log.error("❌ Failed to upload property photo {} for {}: {}", (i + 1), request.getColiveName(), e.getMessage());
                    }
                }
            }

            if (!uploadedPaths.isEmpty()) {
                log.info("✅ {} property photos uploaded for coLive: {}", uploadedPaths.size(), request.getColiveName());
            }
            log.info("✅ ADD CLIENT WITH PROPERTY PHOTOS SUCCESS - Client: {}", request.getColiveName());
                clientAndRoomOnBoardId.setUploadedPhotos(uploadedPaths);
        } else {
            log.info("ℹ️ No property photos provided for coLive: {}", request.getColiveName());
        }
        // Step 2: Upload license documents if provided
        java.util.List<String> uploadedDocPaths = new java.util.ArrayList<>();
        if (licenseDocuments != null && !licenseDocuments.isEmpty()) {
            for (int i = 0; i < licenseDocuments.size(); i++) {
                byte[] docBytes = licenseDocuments.get(i);
                if (docBytes != null && docBytes.length > 0) {
                    try {
                        String fileName = fileStorageService.generateFileName(
                                request.getColiveName() + "_license_" + (i + 1),
                                ".pdf",
                                "property_license");
                        String docPath = fileStorageService.uploadFile(fileName, docBytes, "application/pdf");
                        uploadedDocPaths.add(docPath);
                        log.info("✅ License document {} uploaded for coLive {}: {}", (i + 1), request.getColiveName(), docPath);
                    } catch (Exception e) {
                        log.error("❌ Failed to upload license document {} for {}: {}", (i + 1), request.getColiveName(), e.getMessage());
                    }
                }
            }

            if (!uploadedDocPaths.isEmpty()) {
                log.info("✅ {} license documents uploaded for coLive: {}", uploadedDocPaths.size(), request.getColiveName());
            }
            log.info("✅ ADD CLIENT WITH LICENSE DOCUMENTS SUCCESS - Client: {}", request.getColiveName());
            clientAndRoomOnBoardId.setLicenseDocumentsPath(uploadedDocPaths);
        } else {
            log.info("ℹ️ No license documents provided for coLive: {}", request.getColiveName());
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

   /*
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
        // Call createUser with empty maps since files are handled separately
        ResponseEntity<?> createResponse = createUser(request, new java.util.HashMap<>(), new java.util.HashMap<>());

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

    /**Dowload client file aadharphoto for a specific user
     * Uses FileStorageService.downloadFile()
     *
     * @param userId User ID
     * @return File content as byte array
     */

    public byte[] downloadUserAadharForSignup(String userId) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                String aadharPath = user.getAadharPhotoPath();

                if (aadharPath == null || aadharPath.isBlank()) {
                    throw new IllegalArgumentException("No aadhar photo found for user: " + userId);
                }

                // downloadFile() - Retrieve from storage
                byte[] content = fileStorageService.downloadFile(aadharPath);
                log.info("User aadhar photo downloaded for userId: {}, size: {} bytes", userId, content.length);
                return content;
            }
            throw new IllegalArgumentException("User not found: " + userId);
        } catch (Exception e) {
            log.error("Failed to download aadhar for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to download file: " + e.getMessage());
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

    /**
     * Create user with file uploads (user aadhar + property photos for each coLive)
     *
     * @param request user creation payload
     * @param userAadharBytes user aadhar photo (optional)
     * @param propertyPhotosMap property photos for each coLive (optional)
     *        Format: Map<coliveName, List<photoBytes>>
     * @return 201 with created user data including file paths
     */
    @SuppressWarnings("rawtypes")
    public ResponseEntity createUserWithFilesAndPhotos(UserCreateRequest request,
                                                        byte[] userAadharBytes,
                                                        java.util.Map<String, java.util.List<byte[]>> propertyPhotosMap,
                                                        java.util.Map<String, java.util.List<byte[]>> licenseDocumentsMap) {
        try {
            log.info("📸 SIGNUP WITH FILES AND PHOTOS - Username: {}, Has aadhar: {}, CoLives with photos: {}",
                    request.getUsername(), userAadharBytes != null, propertyPhotosMap.size());

            // Step 1: Create user normally
            ResponseEntity<?> userResponse = createUser(request, propertyPhotosMap, licenseDocumentsMap);

            if (!userResponse.getStatusCode().is2xxSuccessful()) {
                log.warn("❌ SIGNUP WITH FILES - User creation failed");
                return userResponse;
            }

            UserResponse createdUser = (UserResponse) userResponse.getBody();
            String userId = createdUser.getId();
            log.info("✅ User created: {}, ID: {}", createdUser.getUsername(), userId);

            // Step 2: Upload user aadhar if provided
            String userAadharPath = null;
            if (userAadharBytes != null && userAadharBytes.length > 0) {
                try {
                    String fileName = fileStorageService.generateFileName(userId, "aadhar.jpg", "user_aadhar");
                    userAadharPath = fileStorageService.uploadFile(fileName, userAadharBytes, "image/jpeg");
                    log.info("✅ User aadhar uploaded: {}", userAadharPath);
                } catch (Exception e) {
                    log.error("❌ Failed to upload user aadhar: {}", e.getMessage(), e);
                }
            }

            // Step 4: Save file paths to repository
            updateUserWithFilePaths(userId, userAadharPath);

            log.info("✅ SIGNUP WITH FILES AND PHOTOS SUCCESS - User: {}", createdUser.getUsername());
            return userResponse;

        } catch (Exception e) {
            log.error("❌ SIGNUP WITH FILES AND PHOTOS ERROR - Exception: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.internalServerError(e.getMessage()));
        }
    }

    /**
     * Update user with file paths (aadhar and property photos)
     * Saves paths to user document in database
     *
     * @param userId User ID
     * @param userAadharPath User aadhar photo path
     */
    private void updateUserWithFilePaths(String userId, String userAadharPath) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                boolean updated = false;

                // Save user aadhar path
                if (userAadharPath != null && !userAadharPath.isBlank()) {
                    user.setAadharPhotoPath(userAadharPath);
                    updated = true;
                    log.info("✅ User aadhar path saved to repository: {}", userAadharPath);
                }

                // Save user if any updates were made
                if (updated) {
                    user.setUpdatedAt(java.time.Instant.now());
                    userRepository.save(user);
                    log.info("✅ User document updated and saved with file paths");
                }
            }
        } catch (Exception e) {
            log.error("❌ Failed to save file paths to repository: {}", e.getMessage(), e);
        }
    }

    /**
     * Add client to existing user with property photos
     *
     * @param request add client to user payload
     * @param propertyPhotos list of property photo bytes (optional)
     * @return 200 with updated user data including property photo paths
     */
    @SuppressWarnings("rawtypes")
    public ResponseEntity addClientToUserWithFilesAndPhotos(AddClientToUser request,
                                                            List<byte[]> propertyPhotos, List<byte[]> licenseDocuments) {
        try {
            log.info("🏢 ADD CLIENT WITH PROPERTY PHOTOS - Username: {}, CoLiveName: {}, Photos: {}",
                    request.getUsername(), request.getColiveName(), propertyPhotos != null ? propertyPhotos.size() : 0);

            // Step 1: Add client normally
            ResponseEntity<?> addClientResponse = addClientToExistUser(request ,propertyPhotos, licenseDocuments);

            if (!addClientResponse.getStatusCode().is2xxSuccessful()) {
                log.warn("❌ ADD CLIENT WITH PHOTOS - Client addition failed");
                return addClientResponse;
            }

            UserResponse updatedUser = (UserResponse) addClientResponse.getBody();
            String userId = updatedUser.getId();
            String coliveName = request.getColiveName();
            log.info("✅ Client added: {}, User ID: {}", coliveName, userId);

            return addClientResponse;

        } catch (Exception e) {
            log.error("❌ ADD CLIENT WITH PROPERTY PHOTOS ERROR - Exception: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.internalServerError(e.getMessage()));
        }
    }

    /**
     * Update client with property photo paths
     * Saves property photo paths to specific client details in user document
     *
     * @param userId User ID
     * @param coliveName CoLive name
     * @param photoPaths List of property photo paths
     */
    private void updateClientPropertyPhotoPaths(String userId, String coliveName,
                                                 java.util.List<String> photoPaths) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Set<ClientAndRoomOnBoardId> clientDetails = user.getClientDetails();

                if (clientDetails != null) {
                    for (ClientAndRoomOnBoardId client : clientDetails) {
                        if (client.getColiveName().equals(coliveName)) {
                            client.setUploadedPhotos(photoPaths);
                            log.info("✅ Property photo paths set for client: {}", coliveName);
                            break;
                        }
                    }

                    user.setUpdatedAt(java.time.Instant.now());
                    userRepository.save(user);
                    log.info("✅ Client property photo paths saved to repository: {} paths for {}", photoPaths.size(), coliveName);
                }
            }
        } catch (Exception e) {
            log.error("❌ Failed to save property photo paths for client {}: {}", coliveName, e.getMessage(), e);
        }
    }

    /**
     * Download an uploaded photo by file path
     * @param filePath the path of the photo file
     * @return ResponseEntity with the file content
     */
    public ResponseEntity<?> downloadUploadedPhoto(String filePath) {
        try {
            if (filePath == null || filePath.trim().isEmpty()) {
                log.warn("❌ File path is required for download");
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest("INVALID_PATH", "File path is required"));
            }

            if (!fileStorageService.fileExists(filePath)) {
                log.warn("❌ File not found at path: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            byte[] fileContent = fileStorageService.downloadFile(filePath);

            // Extract filename from path for content disposition
            String filename = extractFilenameFromPath(filePath);

            log.info("✅ Photo downloaded successfully: {}", filePath);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", "image/jpeg")
                    .body(fileContent);
        } catch (Exception e) {
            log.error("❌ Error downloading photo from path {}: {}", filePath, e.getMessage(), e);
            return ResponseEntity.status(500).body(ErrorResponse.internalServerError("Failed to download photo: " + e.getMessage()));
        }
    }

    /**
     * Download a license document by file path
     * @param filePath the path of the license document file
     * @return ResponseEntity with the file content
     */
    public ResponseEntity<?> downloadLicenseDocument(String filePath) {
        try {
            if (filePath == null || filePath.trim().isEmpty()) {
                log.warn("❌ File path is required for download");
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest("INVALID_PATH", "File path is required"));
            }

            if (!fileStorageService.fileExists(filePath)) {
                log.warn("❌ File not found at path: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            byte[] fileContent = fileStorageService.downloadFile(filePath);

            // Extract filename from path for content disposition
            String filename = extractFilenameFromPath(filePath);

            log.info("✅ License document downloaded successfully: {}", filePath);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", "application/pdf")
                    .body(fileContent);
        } catch (Exception e) {
            log.error("❌ Error downloading license document from path {}: {}", filePath, e.getMessage(), e);
            return ResponseEntity.status(500).body(ErrorResponse.internalServerError("Failed to download document: " + e.getMessage()));
        }
    }

    /**
     * Delete an uploaded photo by file path and update user record
     * @param username the username of the owner
     * @param coliveName the coLive name associated with the photo
     * @param filePath the path of the photo file to delete
     * @return ResponseEntity with success/error message
     */
    public ResponseEntity<?> deleteUploadedPhoto(String username, String coliveName, String filePath) {
        try {
            if (filePath == null || filePath.trim().isEmpty()) {
                log.warn("❌ File path is required for deletion");
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest("INVALID_PATH", "File path is required"));
            }

            if (!fileStorageService.fileExists(filePath)) {
                log.warn("❌ File not found at path: {}", filePath);
                return ResponseEntity.badRequest().body(ErrorResponse.notFound("File not found"));
            }

            // Get user and remove photo from their clientDetails
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                log.warn("❌ User not found: {}", username);
                return ResponseEntity.badRequest().body(ErrorResponse.notFound("User not found"));
            }

            User user = userOpt.get();
            Set<ClientAndRoomOnBoardId> clientDetails = user.getClientDetails();

            if (clientDetails != null) {
                for (ClientAndRoomOnBoardId client : clientDetails) {
                    if (client.getColiveName().equals(coliveName)) {
                        List<String> uploadedPhotos = client.getUploadedPhotos();
                        if (uploadedPhotos != null && uploadedPhotos.remove(filePath)) {
                            // Delete the file from storage
                            fileStorageService.deleteFile(filePath);

                            // Update timestamp and save user
                            user.setUpdatedAt(java.time.Instant.now());
                            userRepository.save(user);

                            log.info("✅ Photo deleted successfully for user {} from coLive {}: {}", username, coliveName, filePath);
                            return ResponseEntity.ok("Photo deleted successfully");
                        }
                    }
                }
            }

            log.warn("❌ Photo not found in user's records for user {} and coLive {}", username, coliveName);
            return ResponseEntity.badRequest().body(ErrorResponse.notFound("Photo not found in user's records"));
        } catch (Exception e) {
            log.error("❌ Error deleting photo for user {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(500).body(ErrorResponse.internalServerError("Failed to delete photo: " + e.getMessage()));
        }
    }

    /**
     * Delete a license document by file path and update user record
     * @param username the username of the owner
     * @param coliveName the coLive name associated with the document
     * @param filePath the path of the document file to delete
     * @return ResponseEntity with success/error message
     */
    public ResponseEntity<?> deleteLicenseDocument(String username, String coliveName, String filePath) {
        try {
            if (filePath == null || filePath.trim().isEmpty()) {
                log.warn("❌ File path is required for deletion");
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest("INVALID_PATH", "File path is required"));
            }

            if (!fileStorageService.fileExists(filePath)) {
                log.warn("❌ File not found at path: {}", filePath);
                return ResponseEntity.badRequest().body(ErrorResponse.notFound("File not found"));
            }

            // Get user and remove document from their clientDetails
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                log.warn("❌ User not found: {}", username);
                return ResponseEntity.badRequest().body(ErrorResponse.notFound("User not found"));
            }

            User user = userOpt.get();
            Set<ClientAndRoomOnBoardId> clientDetails = user.getClientDetails();

            if (clientDetails != null) {
                for (ClientAndRoomOnBoardId client : clientDetails) {
                    if (client.getColiveName().equals(coliveName)) {
                        List<String> licenseDocuments = client.getLicenseDocumentsPath();
                        if (licenseDocuments != null && licenseDocuments.remove(filePath)) {
                            // Delete the file from storage
                            fileStorageService.deleteFile(filePath);

                            // Update timestamp and save user
                            user.setUpdatedAt(java.time.Instant.now());
                            userRepository.save(user);

                            log.info("✅ License document deleted successfully for user {} from coLive {}: {}", username, coliveName, filePath);
                            return ResponseEntity.ok("License document deleted successfully");
                        }
                    }
                }
            }

            log.warn("❌ License document not found in user's records for user {} and coLive {}", username, coliveName);
            return ResponseEntity.badRequest().body(ErrorResponse.notFound("License document not found in user's records"));
        } catch (Exception e) {
            log.error("❌ Error deleting license document for user {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(500).body(ErrorResponse.internalServerError("Failed to delete document: " + e.getMessage()));
        }
    }

    /**
     * Extract filename from file path (handles both Windows and Unix paths)
     * @param filePath the full file path
     * @return the filename
     */
    private String extractFilenameFromPath(String filePath) {
        if (filePath == null) {
            return "file";
        }

        // Handle Windows and Unix paths
        int lastSeparator = Math.max(filePath.lastIndexOf('\\'), filePath.lastIndexOf('/'));
        if (lastSeparator != -1) {
            return filePath.substring(lastSeparator + 1);
        }
        return filePath;
    }

    /**
     * Extract file extension from filename
     * @param fileName the filename
     * @return the file extension (e.g., "jpg", "pdf") or empty string if no extension
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * Get all uploaded photos for a user's specific coLive
     * @param username the username of the property owner
     * @param coliveName the name of the coLive
     * Get all uploaded photos with file content for a user's specific coLive
     * @param username the username of the property owner
     * @param coliveName the name of the coLive
     * @return ResponseEntity with list of photo file details including content
     */
    public ResponseEntity<?> getUploadedPhotosForCoLive(String username, String coliveName) {
        try {
            if (username == null || username.trim().isEmpty()) {
                log.warn("❌ Username is required");
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest("INVALID_USERNAME", "Username is required"));
            }

            if (coliveName == null || coliveName.trim().isEmpty()) {
                log.warn("❌ CoLive name is required");
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest("INVALID_COLIVE", "CoLive name is required"));
            }

            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                log.warn("❌ User not found: {}", username);
                ResponseEntity.badRequest().body(ErrorResponse.notFound("User not found"));
            }

            User user = userOpt.get();
            Set<ClientAndRoomOnBoardId> clientDetails = user.getClientDetails();

            if (clientDetails == null || clientDetails.isEmpty()) {
                log.warn("❌ No coLives found for user: {}", username);
                return ResponseEntity.badRequest().body(ErrorResponse.notFound("No coLives found for this user"));
            }

            for (ClientAndRoomOnBoardId client : clientDetails) {
                if (client.getColiveName().equalsIgnoreCase(coliveName)) {
                    List<String> uploadedPhotos = client.getUploadedPhotos();
                    if (uploadedPhotos == null || uploadedPhotos.isEmpty()) {
                        log.info("✅ No photos found for coLive: {}", coliveName);
                        return ResponseEntity.ok(new java.util.ArrayList<>());
                    }

                    // Fetch file content and build file details list
                    List<FileDetails> photoDetailsList = new java.util.ArrayList<>();
                    for (String filePath : uploadedPhotos) {
                        try {
                            if (fileStorageService.fileExists(filePath)) {
                                byte[] fileContent = fileStorageService.downloadFile(filePath);
                                String fileName = extractFilenameFromPath(filePath);
                                String fileExtension = getFileExtension(fileName);

                                FileDetails fileDetails = FileDetails.builder()
                                        .fileName(fileName)
                                        .filePath(filePath)
                                        .fileSize((long) fileContent.length)
                                        .fileType("image/jpeg")
                                        .fileExtension(fileExtension)
                                        .fileContent(fileContent)
                                        .exists(true)
                                        .createdAt(java.time.Instant.now())
                                        .description("Property photo for coLive: " + coliveName)
                                        .build();

                                photoDetailsList.add(fileDetails);
                                log.info("✅ Loaded photo: {} ({} bytes)", fileName, fileContent.length);
                            } else {
                                log.warn("⚠️ Photo file not found: {}", filePath);
                                // Still add to list but mark as not existing
                                FileDetails fileDetails = FileDetails.builder()
                                        .filePath(filePath)
                                        .fileName(extractFilenameFromPath(filePath))
                                        .exists(false)
                                        .description("Property photo for coLive: " + coliveName)
                                        .build();
                                photoDetailsList.add(fileDetails);
                            }
                        } catch (Exception e) {
                            log.error("❌ Error loading photo {}: {}", filePath, e.getMessage());
                        }
                    }

                    log.info("✅ Retrieved {} photos for coLive: {}", photoDetailsList.size(), coliveName);
                    return ResponseEntity.ok(photoDetailsList);
                }
            }

            log.warn("❌ CoLive not found for user {}: {}", username, coliveName);
            return ResponseEntity.badRequest().body(ErrorResponse.notFound("CoLive '" + coliveName + "' not found for this user"));
        } catch (Exception e) {
            log.error("❌ Error retrieving photos for user {} and coLive {}: {}", username, coliveName, e.getMessage(), e);
            return ResponseEntity.status(500).body(ErrorResponse.internalServerError("Failed to retrieve photos: " + e.getMessage()));
        }
    }

    /**
     * Get all license documents with file content for a user's specific coLive
     * @param username the username of the property owner
     * @param coliveName the name of the coLive
     * @return ResponseEntity with list of document file details including content
     */
    public ResponseEntity<?> getLicenseDocumentsForCoLive(String username, String coliveName) {
        try {
            if (username == null || username.trim().isEmpty()) {
                log.warn("❌ Username is required");
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest("INVALID_USERNAME", "Username is required"));
            }

            if (coliveName == null || coliveName.trim().isEmpty()) {
                log.warn("❌ CoLive name is required");
                return ResponseEntity.badRequest().body(ErrorResponse.badRequest("INVALID_COLIVE", "CoLive name is required"));
            }

            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                log.warn("❌ User not found: {}", username);
                return ResponseEntity.badRequest().body(ErrorResponse.notFound("User not found"));
            }

            User user = userOpt.get();
            Set<ClientAndRoomOnBoardId> clientDetails = user.getClientDetails();

            if (clientDetails == null || clientDetails.isEmpty()) {
                log.warn("❌ No coLives found for user: {}", username);
                return ResponseEntity.badRequest().body(ErrorResponse.notFound("No coLives found for this user"));
            }

            for (ClientAndRoomOnBoardId client : clientDetails) {
                if (client.getColiveName().equalsIgnoreCase(coliveName)) {
                    List<String> licenseDocuments = client.getLicenseDocumentsPath();
                    if (licenseDocuments == null || licenseDocuments.isEmpty()) {
                        log.info("✅ No documents found for coLive: {}", coliveName);
                        return ResponseEntity.ok(new java.util.ArrayList<>());
                    }

                    // Fetch file content and build file details list
                    List<FileDetails> documentDetailsList = new java.util.ArrayList<>();
                    for (String filePath : licenseDocuments) {
                        try {
                            if (fileStorageService.fileExists(filePath)) {
                                byte[] fileContent = fileStorageService.downloadFile(filePath);
                                String fileName = extractFilenameFromPath(filePath);
                                String fileExtension = getFileExtension(fileName);

                                FileDetails fileDetails = FileDetails.builder()
                                        .fileName(fileName)
                                        .filePath(filePath)
                                        .fileSize((long) fileContent.length)
                                        .fileType("application/pdf")
                                        .fileExtension(fileExtension)
                                        .fileContent(fileContent)
                                        .exists(true)
                                        .createdAt(java.time.Instant.now())
                                        .description("License document for coLive: " + coliveName)
                                        .build();

                                documentDetailsList.add(fileDetails);
                                log.info("✅ Loaded document: {} ({} bytes)", fileName, fileContent.length);
                            } else {
                                log.warn("⚠️ Document file not found: {}", filePath);
                                // Still add to list but mark as not existing
                                FileDetails fileDetails = FileDetails.builder()
                                        .filePath(filePath)
                                        .fileName(extractFilenameFromPath(filePath))
                                        .exists(false)
                                        .description("License document for coLive: " + coliveName)
                                        .build();
                                documentDetailsList.add(fileDetails);
                            }
                        } catch (Exception e) {
                            log.error("❌ Error loading document {}: {}", filePath, e.getMessage());
                        }
                    }

                    log.info("✅ Retrieved {} documents for coLive: {}", documentDetailsList.size(), coliveName);
                    return ResponseEntity.ok(documentDetailsList);
                }
            }

            log.warn("❌ CoLive not found for user {}: {}", username, coliveName);
            return ResponseEntity.badRequest().body(ErrorResponse.notFound("CoLive '" + coliveName + "' not found for this user"));
        } catch (Exception e) {
            log.error("❌ Error retrieving documents for user {} and coLive {}: {}", username, coliveName, e.getMessage(), e);
            return ResponseEntity.status(500).body(ErrorResponse.internalServerError("Failed to retrieve documents: " + e.getMessage()));
        }
    }
}

