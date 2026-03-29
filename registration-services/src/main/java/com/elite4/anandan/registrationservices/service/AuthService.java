package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.dto.JwtResponse;
import com.elite4.anandan.registrationservices.dto.LoginRequest;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import com.elite4.anandan.registrationservices.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Core login/authentication logic that validates credentials
 * and returns a JWT response.
 */
@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Authenticates the user and returns a JWT response if successful.
     * NOW supports login by: email OR phoneNumber
     *
     * @param request login payload (email or phoneNumber + password)
     * @return 200 with JWT response, or 401 if credentials are invalid or account is not active
     */
    public ResponseEntity<?> login(LoginRequest request) {
        try {
            log.info("Login attempt with email: {}, phone: {}", request.getEmail(), request.getPhoneNumber());

            // Find user by email OR phoneNumber (not username)
            User user = null;

            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                // Login with email
                log.debug("Looking up user by email: {}", request.getEmail());
                user = userRepository.findByEmail(request.getEmail()).orElse(null);
                if (user == null) {
                    log.warn("User not found with email: {}", request.getEmail());
                    return ResponseEntity.status(401).body("Invalid email or password");
                }
            } else if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
                // Login with phone number
                log.debug("Looking up user by phone: {}", request.getPhoneNumber());
                user = userRepository.findByPhoneRaw(request.getPhoneNumber()).orElse(null);
                if (user == null) {
                    log.warn("User not found with phone: {}", request.getPhoneNumber());
                    return ResponseEntity.status(401).body("Invalid phone number or password");
                }
            } else {
                log.warn("No email or phone number provided in login request");
                return ResponseEntity.status(400).body("Email or phone number must be provided");
            }

            // Validate password
            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                log.warn("Invalid password for user: {}", user.getUsername());
                return ResponseEntity.status(401).body("Invalid email/phone or password");
            }

            // Check if account is active
            if (!user.isActive()) {
                log.warn("Account inactive for user: {}", user.getUsername());
                return ResponseEntity.status(403).body("Account is pending approval. Please contact administrator.");
            }

            // Generate JWT token using email or phone (not username)
            String identifier = request.getEmail() != null ? request.getEmail() : request.getPhoneNumber();
            log.debug("Generating JWT token for: {}", identifier);
            String token = jwtTokenProvider.generateToken(identifier);

            JwtResponse jwtResponse = new JwtResponse(
                    token,
                    user.getId(),
                    user.getUsername(),
                    user.getEmail()
            );

            log.info("Login successful for user: {}", user.getUsername());
            return ResponseEntity.ok(jwtResponse);

        } catch (Exception e) {
            log.error("Login failed with exception: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred during login: " + e.getMessage());
        }
    }
}

