package com.elite4.anandan.registrationservices.controller;


import com.elite4.anandan.registrationservices.dto.*;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import com.elite4.anandan.registrationservices.service.AdminService;
import com.elite4.anandan.registrationservices.service.AuthService;
import com.elite4.anandan.registrationservices.service.UserCreationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CrossOrigin;
import java.util.List;
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
     * Signup API that checks username and email uniqueness before creating a user.
     *
     * @param request user creation payload
     * @return 201 with created user data, or 400 if username/email already exist
     */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody UserCreateRequest request) {
        try {
            log.info("📝 SIGNUP REQUEST - Username: {}, Email: {}", request.getUsername(), request.getEmail());
            log.debug("📝 SIGNUP REQUEST DETAILS - Phone: {}, Active: {}, ColiveDetails: {}",
                    request.getPhoneNumber(), request.isActive(), request.getColiveDetails());

            ResponseEntity<?> response = userCreationService.createUser(request);

            if (response.getStatusCode() == HttpStatus.CREATED) {
                log.info("✅ SIGNUP SUCCESS - Username: {} created successfully", request.getUsername());
            } else {
                log.warn("⚠️ SIGNUP FAILED - Username: {}, Status: {}, Message: {}",
                        request.getUsername(), response.getStatusCode(), response.getBody());
            }
            return response;
        } catch (Exception e) {
            log.error("❌ SIGNUP ERROR - Username: {}, Exception: {}", request.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Signup failed: " + e.getMessage());
        }
    }

    /**
     * Add client to existing user
     */
    @PostMapping("/addClientToUser")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> addColive(@Valid @RequestBody AddClientToUser request) {
        try {
            log.info("🏢 ADD CLIENT REQUEST - Username: {}, ClientName: {}",
                    request.getUsername(), request.getColiveName());
            log.debug("🏢 ADD CLIENT DETAILS - Email: {}, Phone: {}, CategoryType: {}",
                    request.getEmail(), request.getPhoneNumber(), request.getCategoryType());

            ResponseEntity<?> response = userCreationService.addClientToExistUser(request);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ ADD CLIENT SUCCESS - Username: {}, ClientName: {} added",
                        request.getUsername(), request.getColiveName());
            } else {
                log.warn("⚠️ ADD CLIENT FAILED - Username: {}, ClientName: {}, Status: {}, Message: {}",
                        request.getUsername(), request.getColiveName(), response.getStatusCode(), response.getBody());
            }

            return response;
        } catch (Exception e) {
            log.error("❌ ADD CLIENT ERROR - Username: {}, ClientName: {}, Exception: {}",
                    request.getUsername(), request.getColiveName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Add client failed: " + e.getMessage());
        }
    }

    /**
     * Add client to existing user with file uploads
     */
    @PostMapping("/addClientToUser-with-files")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> addClientWithFiles(
            @RequestParam String request,
            @RequestParam(required = false) org.springframework.web.multipart.MultipartFile aadharPhoto,
            @RequestParam(required = false) org.springframework.web.multipart.MultipartFile document) throws java.io.IOException {

        try {
            log.info("📸 ADD CLIENT WITH FILES REQUEST - Aadhar File: {}, Document File: {}",
                    aadharPhoto != null ? aadharPhoto.getOriginalFilename() : "None",
                    document != null ? document.getOriginalFilename() : "None");
            log.debug("📸 ADD CLIENT FILES SIZE - Aadhar: {} bytes, Document: {} bytes",
                    aadharPhoto != null ? aadharPhoto.getSize() : 0,
                    document != null ? document.getSize() : 0);

            // Parse JSON request
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            AddClientToUser addClientRequest = mapper.readValue(request, AddClientToUser.class);
            log.debug("📸 ADD CLIENT WITH FILES - Parsed Request: Username: {}, ClientName: {}",
                    addClientRequest.getUsername(), addClientRequest.getColiveName());

            // Build client files map from multipart files
            java.util.Map<String, byte[]> clientFilesMap = new java.util.HashMap<>();

            if (aadharPhoto != null && !aadharPhoto.isEmpty()) {
                clientFilesMap.put("aadhar", aadharPhoto.getBytes());
                log.debug("📸 Aadhar photo prepared for upload: {}", aadharPhoto.getOriginalFilename());
            }

            if (document != null && !document.isEmpty()) {
                clientFilesMap.put("document", document.getBytes());
                log.debug("📸 Document prepared for upload: {}", document.getOriginalFilename());
            }

            ResponseEntity<?> response = userCreationService.addClientToUserWithFiles(addClientRequest, clientFilesMap);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ ADD CLIENT WITH FILES SUCCESS - Username: {}, ClientName: {}, Files uploaded",
                        addClientRequest.getUsername(), addClientRequest.getColiveName());
            } else {
                log.warn("⚠️ ADD CLIENT WITH FILES FAILED - Username: {}, Status: {}",
                        addClientRequest.getUsername(), response.getStatusCode());
            }

            return response;
        } catch (Exception e) {
            log.error("❌ ADD CLIENT WITH FILES ERROR - Exception: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Add client with files failed: " + e.getMessage());
        }
    }

    /**
     * Login API that authenticates user and returns a JWT token.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            log.info("🔐 LOGIN REQUEST - Email: {}, Phone: {}", request.getEmail(), request.getPhoneNumber());

            ResponseEntity<?> response = authService.login(request);

            if (response.getStatusCode() == HttpStatus.OK) {
                Object body = response.getBody();
                if (body instanceof JwtResponse) {
                    JwtResponse jwtResponse = (JwtResponse) body;
                    log.info("✅ LOGIN SUCCESS - User: {}, Token Generated", jwtResponse.getUsername());
                    log.debug("✅ LOGIN TOKEN DETAILS - UserId: {}, Email: {}",
                            jwtResponse.getUserId(), jwtResponse.getEmail());
                }
            } else {
                log.warn("⚠️ LOGIN FAILED - Email: {}, Status: {}, Message: {}",
                        request.getEmail(), response.getStatusCode(), response.getBody());
            }

            return response;
        } catch (Exception e) {
            log.error("❌ LOGIN ERROR - Email: {}, Exception: {}", request.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Login failed: " + e.getMessage());
        }
    }

    /**
     * Get all users pending approval (inactive users).
     */
    @GetMapping("/admin/pending-approvals")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<List<UserResponse>> getPendingApprovals() {
        try {
            log.info("📋 GET PENDING APPROVALS REQUEST");

            List<UserResponse> response = adminService.getPendingApprovals();

            log.info("✅ GET PENDING APPROVALS SUCCESS - Found {} pending users", response.size());
            log.debug("📋 Pending users: {}", response.stream()
                    .map(UserResponse::getUsername)
                    .toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ GET PENDING APPROVALS ERROR - Exception: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get all active users.
     */
    @GetMapping("/admin/active-users")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<List<UserResponse>> getActiveUsers() {
        try {
            log.info("📋 GET ACTIVE USERS REQUEST");

            List<UserResponse> response = adminService.getActiveUsers();

            log.info("✅ GET ACTIVE USERS SUCCESS - Found {} active users", response.size());
            log.debug("📋 Active users: {}", response.stream()
                    .map(UserResponse::getUsername)
                    .toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ GET ACTIVE USERS ERROR - Exception: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Approve a user registration.
     */
    @PostMapping("/admin/approve/{username}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> approveUser(@PathVariable String username) {
        try {
            log.info("✔️ APPROVE USER REQUEST - Username: {}", username);

            ResponseEntity<?> response = adminService.approveUser(username);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ APPROVE USER SUCCESS - Username: {} approved", username);
            } else {
                log.warn("⚠️ APPROVE USER FAILED - Username: {}, Status: {}, Message: {}",
                        username, response.getStatusCode(), response.getBody());
            }

            return response;
        } catch (Exception e) {
            log.error("❌ APPROVE USER ERROR - Username: {}, Exception: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Approve user failed: " + e.getMessage());
        }
    }

    /**
     * Reject a user registration (delete inactive user).
     */
    @DeleteMapping("/admin/reject/{username}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> rejectUser(@PathVariable String username) {
        try {
            log.info("❌ REJECT USER REQUEST - Username: {}", username);

            ResponseEntity<?> response = adminService.rejectUser(username);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ REJECT USER SUCCESS - Username: {} rejected", username);
            } else {
                log.warn("⚠️ REJECT USER FAILED - Username: {}, Status: {}", username, response.getStatusCode());
            }

            return response;
        } catch (Exception e) {
            log.error("❌ REJECT USER ERROR - Username: {}, Exception: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Reject user failed: " + e.getMessage());
        }
    }

    /**
     * Deactivate an active user.
     */
    @PostMapping("/admin/deactivate/{username}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> deactivateUser(@PathVariable String username) {
        try {
            log.info("⏸️ DEACTIVATE USER REQUEST - Username: {}", username);

            ResponseEntity<?> response = adminService.deactivateUser(username);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ DEACTIVATE USER SUCCESS - Username: {} deactivated", username);
            } else {
                log.warn("⚠️ DEACTIVATE USER FAILED - Username: {}, Status: {}", username, response.getStatusCode());
            }

            return response;
        } catch (Exception e) {
            log.error("❌ DEACTIVATE USER ERROR - Username: {}, Exception: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Deactivate user failed: " + e.getMessage());
        }
    }

    /**
     * Get user details by username.
     */
    @GetMapping("/admin/user/{username}")
    @PreAuthorize("hasAnyRole('USER','ADMIN','MODERATOR')")
    public ResponseEntity<?> getUserByUsername(@PathVariable String username) {
        try {
            log.info("👤 GET USER BY USERNAME REQUEST - Username: {}", username);

            UserResponse userResponse = adminService.getUserWithRoles(username);

            if (userResponse != null) {
                log.info("✅ GET USER SUCCESS - Username: {}, Email: {}, Active: {}",
                        userResponse.getUsername(), userResponse.getEmail(), userResponse.isActive());
                log.debug("👤 USER DETAILS - ID: {}, RoleIds: {}, Phones: {}",
                        userResponse.getId(), userResponse.getRoleIds(), userResponse.getPhoneNumber());
            } else {
                log.warn("⚠️ GET USER NOT FOUND - Username: {}", username);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(userResponse);
        } catch (Exception e) {
            log.error("❌ GET USER ERROR - Username: {}, Exception: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Get user failed: " + e.getMessage());
        }
    }

    /**
     * Search users by username with wildcard support.
     */
    @GetMapping("/admin/search/{username}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<List<UserResponse>> searchUsers(@PathVariable String username) {
        try {
            log.info("🔍 SEARCH USERS REQUEST - Search Term: {}", username);

            List<UserResponse> response = adminService.getUserWithWildCard(username);

            log.info("✅ SEARCH USERS SUCCESS - Found {} users matching: {}", response.size(), username);
            log.debug("🔍 Search results: {}", response.stream()
                    .map(UserResponse::getUsername)
                    .toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ SEARCH USERS ERROR - Search Term: {}, Exception: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
