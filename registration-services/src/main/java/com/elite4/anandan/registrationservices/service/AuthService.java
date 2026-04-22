package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.dto.ChangePasswordRequest;
import com.elite4.anandan.registrationservices.dto.ForgotPasswordRequest;
import com.elite4.anandan.registrationservices.dto.JwtResponse;
import com.elite4.anandan.registrationservices.dto.LoginRequest;
import com.elite4.anandan.registrationservices.dto.ResetPasswordRequest;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import com.elite4.anandan.registrationservices.security.JwtTokenProvider;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Autowired
    private NotificationClient notificationClient;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public ResponseEntity<?> login(LoginRequest request) {
        try {
            log.info("LOGIN ATTEMPT - Email: {}, Phone: {}", request.getEmail(), request.getPhoneNumber());

            User user = null;
            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                user = userRepository.findByEmail(request.getEmail()).orElse(null);
                if (user == null) {
                    return ResponseEntity.status(401).body("Invalid email or password");
                }
            } else if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
                user = userRepository.findByPhoneE164(request.getPhoneNumber()).orElse(null);
                if (user == null) {
                    user = userRepository.findByPhoneRaw(request.getPhoneNumber()).orElse(null);
                }
                if (user == null) {
                    return ResponseEntity.status(401).body("Invalid phone number or password");
                }
            } else {
                return ResponseEntity.status(400).body("Email or phone number must be provided");
            }

            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                return ResponseEntity.status(401).body("Invalid email/phone or password");
            }

            if (!user.isActive()) {
                return ResponseEntity.status(403).body("Account is pending approval. Please contact administrator.");
            }

            String identifier = request.getEmail() != null && !request.getEmail().isBlank()
                    ? request.getEmail()
                    : request.getPhoneNumber();
            String token = jwtTokenProvider.generateToken(identifier);

            user.setLastLoginAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);

            JwtResponse jwtResponse = new JwtResponse(token, user.getId(), user.getUsername(), user.getEmail());
            jwtResponse.setForcePasswordChange(user.isForcePasswordChange());
            return ResponseEntity.ok(jwtResponse);
        } catch (Exception e) {
            log.error("LOGIN FAILED - Exception: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred during login: " + e.getMessage());
        }
    }

    public ResponseEntity<?> changePassword(Authentication authentication, ChangePasswordRequest request) {
        try {
            if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
            }

            String identifier = authentication.getName();
            Optional<User> userOpt = identifier.contains("@")
                    ? userRepository.findByEmail(identifier)
                    : userRepository.findByPhoneE164(identifier).or(() -> userRepository.findByPhoneRaw(identifier));

            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            User user = userOpt.get();
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Current password is incorrect");
            }

            if (request.getCurrentPassword().equals(request.getNewPassword())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("New password must be different from current password");
            }

            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            user.setForcePasswordChange(false);
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);

            return ResponseEntity.ok("Password changed successfully");
        } catch (Exception e) {
            log.error("CHANGE PASSWORD FAILED - Exception: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to change password: " + e.getMessage());
        }
    }

    public ResponseEntity<?> requestPasswordReset(ForgotPasswordRequest request) {
        try {
            User user = null;
            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                user = userRepository.findByEmail(request.getEmail()).orElse(null);
            } else if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
                user = userRepository.findByPhoneE164(request.getPhoneNumber())
                        .or(() -> userRepository.findByPhoneRaw(request.getPhoneNumber()))
                        .orElse(null);
            }

            // Always return the same message to avoid user enumeration
            if (user == null || !user.isActive()) {
                return ResponseEntity.ok("If an account with those details exists, an OTP has been sent.");
            }

            String otp = String.format("%06d", new SecureRandom().nextInt(1_000_000));
            user.setPasswordResetOtp(otp);
            user.setPasswordResetOtpExpiry(Instant.now().plusSeconds(900)); // 15 minutes
            userRepository.save(user);

            log.info("Password reset OTP generated for user: {} [dev log, remove in prod]", user.getUsername());

            String recipient = request.getEmail() != null && !request.getEmail().isBlank()
                    ? request.getEmail() : null;
            if (recipient != null) {
                try {
                    notificationClient.sendEmail(
                            recipient,
                            "CoLive Connect – Password Reset OTP",
                            "Your OTP to reset your password is: " + otp + "\n\nThis OTP is valid for 15 minutes. Do not share it with anyone."
                    );
                } catch (Exception e) {
                    log.warn("Failed to send password reset email to {}: {}", recipient, e.getMessage());
                }
            }

            return ResponseEntity.ok("If an account with those details exists, an OTP has been sent.");
        } catch (Exception e) {
            log.error("REQUEST PASSWORD RESET FAILED: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred. Please try again.");
        }
    }

    public ResponseEntity<?> resetPasswordWithOtp(ResetPasswordRequest request) {
        try {
            if (request.getOtp() == null || request.getOtp().isBlank()) {
                return ResponseEntity.badRequest().body("OTP is required.");
            }
            if (request.getNewPassword() == null || request.getNewPassword().length() < 8) {
                return ResponseEntity.badRequest().body("New password must be at least 8 characters.");
            }

            User user = null;
            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                user = userRepository.findByEmail(request.getEmail()).orElse(null);
            } else if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
                user = userRepository.findByPhoneE164(request.getPhoneNumber())
                        .or(() -> userRepository.findByPhoneRaw(request.getPhoneNumber()))
                        .orElse(null);
            }

            if (user == null) {
                return ResponseEntity.badRequest().body("Invalid OTP or OTP has expired.");
            }

            if (user.getPasswordResetOtp() == null || user.getPasswordResetOtpExpiry() == null
                    || Instant.now().isAfter(user.getPasswordResetOtpExpiry())
                    || !user.getPasswordResetOtp().equals(request.getOtp())) {
                return ResponseEntity.badRequest().body("Invalid OTP or OTP has expired.");
            }

            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            user.setPasswordResetOtp(null);
            user.setPasswordResetOtpExpiry(null);
            user.setForcePasswordChange(false);
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);

            log.info("Password reset successfully for user: {}", user.getUsername());
            return ResponseEntity.ok("Password reset successfully. Please log in with your new password.");
        } catch (Exception e) {
            log.error("RESET PASSWORD WITH OTP FAILED: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred. Please try again.");
        }
    }
}
