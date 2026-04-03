package com.elite4.anandan.registrationservices.controller;


import com.elite4.anandan.registrationservices.dto.*;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import com.elite4.anandan.registrationservices.service.AdminService;
import com.elite4.anandan.registrationservices.service.AuthService;
import com.elite4.anandan.registrationservices.service.UserCreationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.security.core.Authentication;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.elite4.anandan.registrationservices.dto.ColiveListItem;

/**
 * REST controller exposing auth-related APIs such as signup and login.
 */
@RestController
@RequestMapping("/adminservices")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Slf4j
public class UserController {

    private final UserCreationService userCreationService;
    private final AuthService authService;
    private final AdminService adminService;
    private final UserRepository userRepository;

    public UserController(UserCreationService userCreationService,
                          AuthService authService,
                          AdminService adminService,
                          UserRepository userRepository) {
        this.userCreationService = userCreationService;
        this.authService = authService;
        this.adminService = adminService;
        this.userRepository = userRepository;
    }
    /**
     * Signup API with file uploads (user aadhar + property photos for each coLive)
     * Supports multipart file uploads:
     * - User aadhar photo
     * - Property photos for each coLive (multiple files per coLive)
     *
     * @param request user creation request (JSON)
     * @param userAadhar user aadhar photo (optional)
     * @param fileMap property photos for each coLive (optional, format: colive1_property_1, colive1_property_2, etc.)
     * @param licenseDocuments property document for each colive (optional,format: colive1_document,colive2_document,etc.)
     * @return 201 with created user data including file paths
     */
    @PostMapping("/signup-with-files")
    public ResponseEntity<?> signupWithFiles(
            @RequestParam String request,
            @RequestParam(required = false) org.springframework.web.multipart.MultipartFile userAadhar,
            @RequestParam(required = false) java.util.Map<String, org.springframework.web.multipart.MultipartFile> fileMap,
            @RequestParam(required = false) Map<String, org.springframework.web.multipart.MultipartFile> licenseDocuments) throws java.io.IOException {

        try {
            log.info("📝 SIGNUP WITH FILES REQUEST");

            // Parse JSON request
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            UserCreateRequest userCreateRequest = mapper.readValue(request, UserCreateRequest.class);

            log.debug("📝 User aadhar file: {}", userAadhar != null ? userAadhar.getOriginalFilename() : "None");
            log.debug("📝 Property photos count: {}", fileMap != null ? fileMap.size() : 0);
            log.debug("📝 license documents count: {}", licenseDocuments != null ? licenseDocuments.size() : 0);


            // Build maps for user aadhar and property photos per coLive
            byte[] userAadharBytes = null;
            java.util.Map<String, java.util.List<byte[]>> propertyPhotosMap = new java.util.HashMap<>();
            java.util.Map<String, java.util.List<byte[]>> licenseDocumentsMap = new java.util.HashMap<>();

            // Handle user aadhar
            if (userAadhar != null && !userAadhar.isEmpty()) {
                userAadharBytes = userAadhar.getBytes();
                log.debug("📝 User aadhar file size: {} bytes", userAadharBytes.length);
            }

            // Handle property photos for each coLive
            if (fileMap != null && !fileMap.isEmpty()) {
                // Get list of coLives from request
                List<ColiveNameAndRooms> coLivesList = new java.util.ArrayList<>(userCreateRequest.getColiveDetails());

                for (String key : fileMap.keySet()) {
                    org.springframework.web.multipart.MultipartFile file = fileMap.get(key);
                    if (file != null && !file.isEmpty()) {
                        // Parse key format: property_X (e.g., "property_0", "property_1")
                        if (key.contains("photo_")) {
                            try {
                                // Extract property index
                                String[] parts = key.split("_");
                                if (parts.length == 4) {
                                    int propertyIndex = Integer.parseInt(parts[1]);

                                    // Map property_X to coLive at index X
                                    if (propertyIndex < coLivesList.size()) {
                                        String coliveName = coLivesList.get(propertyIndex).getColiveName();
                                        byte[] fileBytes = file.getBytes();

                                        // Add to map with format: coliveName_0_photo_0
                                        propertyPhotosMap.computeIfAbsent(coliveName, k -> new java.util.ArrayList<>())
                                                .add(fileBytes);

                                        log.debug("📝 Property file {} mapped to coLive {} (index {}): {} bytes",
                                                key, coliveName, propertyIndex, fileBytes.length);
                                    } else {
                                        log.warn("📝 Property index {} out of range. Total coLives: {}", propertyIndex, coLivesList.size());
                                    }
                                }
                            } catch (NumberFormatException e) {
                                log.warn("📝 Invalid property index format in key {}: {}", key, e.getMessage());
                            } catch (Exception e) {
                                log.warn("📝 Failed to read file {}: {}", key, e.getMessage());
                            }
                        }
                    }
                }
            }

            // Handle property documents for each coLive
            if (licenseDocuments != null && !licenseDocuments.isEmpty()) {
                // Get list of coLives from request
                List<ColiveNameAndRooms> coLivesList = new java.util.ArrayList<>(userCreateRequest.getColiveDetails());

                for (String key : fileMap.keySet()) {
                    org.springframework.web.multipart.MultipartFile file = licenseDocuments.get(key);
                    if (file != null && !file.isEmpty()) {
                        // Parse key format: property_X (e.g., "license_0", "license_1")
                        if (key.contains("license_")) {
                            try {
                                // Extract property index
                                String[] parts = key.split("_");
                                if (parts.length == 4) {
                                    int propertyIndex = Integer.parseInt(parts[1]);

                                    // Map property_X to coLive at index X
                                    if (propertyIndex < coLivesList.size()) {
                                        String coliveName = coLivesList.get(propertyIndex).getColiveName();
                                        byte[] fileBytes = file.getBytes();

                                        // Add to property photos map
                                        licenseDocumentsMap.computeIfAbsent(coliveName, k -> new java.util.ArrayList<>())
                                                .add(fileBytes);

                                        log.debug("📝 Property file {} mapped to coLive {} (index {}): {} bytes",
                                                key, coliveName, propertyIndex, fileBytes.length);
                                    } else {
                                        log.warn("📝 Property index {} out of range. Total coLives: {}", propertyIndex, coLivesList.size());
                                    }
                                }
                            } catch (NumberFormatException e) {
                                log.warn("📝 Invalid property index format in key {}: {}", key, e.getMessage());
                            } catch (Exception e) {
                                log.warn("📝 Failed to read file {}: {}", key, e.getMessage());
                            }
                        }
                    }
                }
            }
            return userCreationService.createUserWithFilesAndPhotos(userCreateRequest, userAadharBytes, propertyPhotosMap, licenseDocumentsMap);
        } catch (Exception e) {
            log.error("❌ SIGNUP WITH FILES ERROR - Exception: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.internalServerError(e.getMessage()));
        }
    }

    /**
     * Download user aadhar file for a specific user in signup
     */
    @GetMapping("/download-user-aadhar/{userId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN','MODERATOR')")
    public ResponseEntity<byte[]> downloadUserAadhar(@PathVariable String userId) {
        try {
            byte[] fileContent = userCreationService.downloadUserAadharForSignup(userId);
            return ResponseEntity.ok()
                    .header("Content-Type", "image/jpeg")
                    .header("Content-Disposition", "attachment; filename=\"aadhar.jpg\"")
                    .body(fileContent);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Add client to existing user with file uploads (property photos for the coLive)
     * Supports multipart file uploads:
     * - Property photos for the coLive being added (multiple files allowed)
     *
     * Files are mapped to AddClientToUser attributes:
     * - propertyPhotosPath: List of uploaded property photo paths
     *
     * @param request add client to user request (JSON)
     * @param fileMap property photos for the coLive (optional, format: property_1, property_2, etc.)
     * @return 200 with updated user data including file paths
     */
    @PostMapping("/addClientToUser-with-files")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> addClientWithFiles(
            @RequestParam String request,
            @RequestParam(required = false) java.util.Map<String, org.springframework.web.multipart.MultipartFile> fileMap,
            @RequestParam(required = false) Map<String, org.springframework.web.multipart.MultipartFile> licenseDocuments) throws java.io.IOException {

        try {
            log.info("🏢 ADD CLIENT WITH FILES REQUEST");

            // Parse JSON request
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            AddClientToUser addClientRequest = mapper.readValue(request, AddClientToUser.class);

            log.debug("🏢 Username: {}, CoLiveName: {}", addClientRequest.getUsername(), addClientRequest.getColiveName());
            log.debug("🏢 Property photos count: {}", fileMap != null ? fileMap.size() : 0);

            // Build list for property photos
            java.util.List<byte[]> propertyPhotosMap = new java.util.ArrayList<>();
            java.util.List<byte[]> licenseDocumentsMap = new java.util.ArrayList<>();
            // Handle property photos for each coLive
            if (fileMap != null && !fileMap.isEmpty()) {
                for (String key : fileMap.keySet()) {
                    org.springframework.web.multipart.MultipartFile file = fileMap.get(key);
                    if (file != null && !file.isEmpty()) {
                        // Parse key format: property_X (e.g., "property_0", "property_1")
                        if (key.contains("photo_")) {
                            try {
                                byte[] fileBytes = file.getBytes();
                                        // Add to map with format: coliveName_0_photo_0
                                propertyPhotosMap.add(fileBytes);
                                log.debug("📝 Property file {} mapped to coLive {} : {} bytes",
                                                key, addClientRequest.getColiveName(), fileBytes.length);
                            } catch (NumberFormatException e) {
                                log.warn("📝 Invalid property index format in key {}: {}", key, e.getMessage());
                            } catch (Exception e) {
                                log.warn("📝 Failed to read file {}: {}", key, e.getMessage());
                            }
                        }
                    }
                }
            }

            // Handle property documents for each coLive
            if (licenseDocuments != null && !licenseDocuments.isEmpty()) {
                for (String key : fileMap.keySet()) {
                    org.springframework.web.multipart.MultipartFile file = licenseDocuments.get(key);
                    if (file != null && !file.isEmpty()) {
                        // Parse key format: property_X (e.g., "license_0", "license_1")
                        if (key.contains("license_")) {
                            try {
                                        byte[] fileBytes = file.getBytes();
                                        // Add to property photos map
                                        licenseDocumentsMap.add(fileBytes);
                                        log.debug("📝 Property file {} mapped to coLive {}: {} bytes",
                                                key, addClientRequest.getColiveName(), fileBytes.length);
                            } catch (NumberFormatException e) {
                                log.warn("📝 Invalid property index format in key {}: {}", key, e.getMessage());
                            } catch (Exception e) {
                                log.warn("📝 Failed to read file {}: {}", key, e.getMessage());
                            }
                        }
                    }
                }
            }

            return userCreationService.addClientToUserWithFilesAndPhotos(addClientRequest, propertyPhotosMap,licenseDocumentsMap);
        } catch (Exception e) {
            log.error("❌ ADD CLIENT WITH FILES ERROR - Exception: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.internalServerError(e.getMessage()));
        }
    }

    /**
     * Login API that authenticates user and returns a JWT token.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/change-password")
    @PreAuthorize("hasAnyRole('USER','ADMIN','MODERATOR')")
    public ResponseEntity<?> changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(authentication, request);
    }


    /**
     * Get all users pending approval (inactive users).
     * Requires ADMIN or MODERATOR role.
     */
    @GetMapping("/admin/pending-approvals")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<List<UserResponse>> getPendingApprovals() {
        return ResponseEntity.ok(adminService.getPendingApprovals());
    }

    /**
     * Get all active users.
     * Requires ADMIN or MODERATOR role.
     */
    @GetMapping("/admin/active-users")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<List<UserResponse>> getActiveUsers() {
        return ResponseEntity.ok(adminService.getActiveUsers());
    }

    /**
     * Approve a user registration.
     * Requires ADMIN or MODERATOR role.
     */
    @PostMapping("/admin/approve/{username}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> approveUser(@PathVariable String username) {
        return adminService.approveUser(username);
    }

    /**
     * Reject a user registration (delete inactive user).
     * Requires ADMIN or MODERATOR role.
     */
    @DeleteMapping("/admin/reject/{username}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> rejectUser(@PathVariable String username) {
        return adminService.rejectUser(username);
    }

    /**
     * Deactivate an active user.
     * Requires ADMIN or MODERATOR role.
     */
    @PostMapping("/admin/deactivate/{username}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> deactivateUser(@PathVariable String username) {
        return adminService.deactivateUser(username);
    }

    /**
     * Get user details by username.
     * Requires ADMIN or MODERATOR role.
     */
    /**
     * Search active CoLive properties by name for transfer selection.
     * Requires at least 1 character in the search param.
     */
    @GetMapping("/admin/colives")
    @PreAuthorize("hasAnyRole('USER','ADMIN','MODERATOR')")
    public ResponseEntity<List<ColiveListItem>> searchColiveProperties(
            @RequestParam(required = true) String search) {
        return ResponseEntity.ok(adminService.searchColiveProperties(search));
    }

    @GetMapping("/admin/user/{username}")
    @PreAuthorize("hasAnyRole('USER','ADMIN','MODERATOR')")
    public ResponseEntity<?> getUserByUsername(@PathVariable String username) {
        UserResponse userResponse = adminService.getUserClientsWithOutRooms(username);
        return ResponseEntity.ok(userResponse);
    }

    /**
     * Get user details by username and colive name.
     * Requires ADMIN or MODERATOR role.
     */

    @GetMapping("/admin/user/{username}/colive/{coLiveName}")
    @PreAuthorize("hasAnyRole('USER','ADMIN','MODERATOR')")
    public ResponseEntity<?> getUserByUsername(@PathVariable String username,@PathVariable String coLiveName) {
        ColiveNameAndRooms userResponse = adminService.getUserClientsWithRoomsAndUploadedPhotosAndAttachments(username,coLiveName);
        return ResponseEntity.ok(userResponse);
    }

    /**
     * Get user details by username.
     * Requires ADMIN or MODERATOR role.
     */
    @GetMapping("/admin/userWithClientAndRooms/{username}")
    @PreAuthorize("hasAnyRole('USER','ADMIN','MODERATOR')")
    public ResponseEntity<?> getUserByUsernameWithRooms(@PathVariable String username) {
        UserResponse userResponse = adminService.getUserWithRoles(username);
        return ResponseEntity.ok(userResponse);
    }

    /**
     * Search users by username with wildcard support.
     * Requires ADMIN or MODERATOR role.
     */
    @GetMapping("/admin/search/{username}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<List<UserResponse>> searchUsers(@PathVariable String username) {
        return ResponseEntity.ok(adminService.getUserWithWildCard(username));
    }

    /**
     * Get all uploaded photos for a user's specific coLive
     * Requires USER, ADMIN or MODERATOR role.
     */
    @GetMapping("/user/{username}/coLive/{coliveName}/photos")
    @PreAuthorize("hasAnyRole('USER','ADMIN','MODERATOR')")
    public ResponseEntity<?> getUploadedPhotosForCoLive(
            @PathVariable String username,
            @PathVariable String coliveName) {
        return userCreationService.getUploadedPhotosForCoLive(username, coliveName);
    }

    /**
     * Get all license documents for a user's specific coLive
     * Requires USER, ADMIN or MODERATOR role.
     */
    @GetMapping("/user/{username}/coLive/{coliveName}/documents")
    @PreAuthorize("hasAnyRole('USER','ADMIN','MODERATOR')")
    public ResponseEntity<?> getLicenseDocumentsForCoLive(
            @PathVariable String username,
            @PathVariable String coliveName) {
        return userCreationService.getLicenseDocumentsForCoLive(username, coliveName);
    }
}




