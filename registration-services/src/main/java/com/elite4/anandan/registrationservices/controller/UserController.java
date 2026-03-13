package com.elite4.anandan.registrationservices.controller;


import com.elite4.anandan.registrationservices.dto.*;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import com.elite4.anandan.registrationservices.service.AdminService;
import com.elite4.anandan.registrationservices.service.AuthService;
import com.elite4.anandan.registrationservices.service.UserCreationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST controller exposing auth-related APIs such as signup and login.
 */
@RestController
@RequestMapping("/adminservices")
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
        return userCreationService.createUser(request);
    }

    @PostMapping("/addClientToUser")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> addClient(@Valid @RequestBody AddClientToUser request) {
        return userCreationService.addClientToExistUser(request);
    }

    @PutMapping("/updateRoomTypeToUser")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> updateRoomTypeToUser(@Valid @RequestBody UpdateRoomType request) {
        return userCreationService.updateRoomType(request);
    }

    /**
     * Login API that authenticates user and returns a JWT token.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
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
    @GetMapping("/admin/user/{username}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> getUserByUsername(@PathVariable String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        return userOpt.map(user -> ResponseEntity.ok(adminService.toUserResponse(user)))
                .orElse(ResponseEntity.notFound().build());
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
}
