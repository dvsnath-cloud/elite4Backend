package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.dto.JwtResponse;
import com.elite4.anandan.registrationservices.dto.LoginRequest;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import com.elite4.anandan.registrationservices.security.JwtTokenProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Core login/authentication logic that validates credentials
 * and returns a JWT response.
 */
@Service
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
     *
     * @param request login payload
     * @return 200 with JWT response, or 401 if credentials are invalid or account is not active
     */
    public ResponseEntity<?> login(LoginRequest request) {
        User user = userRepository.findAll()
                .stream()
                .filter(u -> request.getUsername().equals(u.getUsername()))
                .findFirst()
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body("Invalid username or password");
        }

        if (!user.isActive()) {
            return ResponseEntity.status(403).body("Account is pending approval. Please contact administrator.");
        }

        String token = jwtTokenProvider.generateToken(user.getUsername());
        JwtResponse jwtResponse = new JwtResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getEmail()
        );
        return ResponseEntity.ok(jwtResponse);
    }
}
