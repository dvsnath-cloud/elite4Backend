package com.elite4.anandan.registrationservices.security;


import com.elite4.anandan.registrationservices.model.Role;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.RoleRepository;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Filter that authenticates requests based on JWT tokens sent in the Authorization header.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, UserRepository userRepository, RoleRepository roleRepository) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String jwt = resolveToken(request);

        if (jwt != null) {
            log.debug("🔐 JWT token found in request. Length: {}", jwt.length());

            if (!tokenProvider.validateToken(jwt)) {
                log.warn("❌ JWT token validation failed");
                filterChain.doFilter(request, response);
                return;
            }
            log.debug("✅ JWT token validated successfully");

            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                log.debug("⚠️ Authentication already exists in SecurityContext, skipping");
                filterChain.doFilter(request, response);
                return;
            }

            try {
                // Get email or phoneNumber from token (not username)
                String emailOrPhone = tokenProvider.getEmailOrPhoneFromToken(jwt);
                log.debug("🔍 Extracted identifier from token: {}", emailOrPhone);

                // Find user by email or phoneNumber
                Optional<User> userOpt = Optional.empty();

                // Try to find by email first
                if (emailOrPhone.contains("@")) {
                    // It's an email (contains @)
                    log.debug("🔍 Attempting to find user by email: {}", emailOrPhone);
                    userOpt = userRepository.findByEmail(emailOrPhone);
                    if (userOpt.isPresent()) {
                        log.info("✅ User found by email: {}", userOpt.get().getUsername());
                    } else {
                        log.warn("❌ No user found with email: {}", emailOrPhone);
                    }
                } else {
                    // It's a phone number
                    log.debug("🔍 Attempting to find user by phone: {}", emailOrPhone);
                    userOpt = userRepository.findByPhoneE164(emailOrPhone);
                    if (userOpt.isEmpty()) {
                        log.debug("⚠️ Phone not found as E164, trying raw phone lookup");
                        userOpt = userRepository.findByPhoneRaw(emailOrPhone);
                    }
                    if (userOpt.isPresent()) {
                        log.info("✅ User found by phone: {}", userOpt.get().getUsername());
                    } else {
                        log.warn("❌ No user found with phone: {}", emailOrPhone);
                    }
                }

                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    log.info("🔐 Setting authentication for user: {}", user.getUsername());

                    Set<String> resolvedRoles = new HashSet<>();
                    for (String value : user.getRoleIds()) {
                        if (value == null || value.isBlank()) {
                            continue;
                        }
                        Optional<Role> savedRole = roleRepository.findById(value);
                        if (savedRole.isPresent()) {
                            resolvedRoles.add(savedRole.get().getName().name());
                            log.debug("✅ Role loaded: {}", savedRole.get().getName().name());
                        } else {
                            log.warn("⚠️ Role not found for ID: {}", value);
                        }
                    }
                    List<SimpleGrantedAuthority> authorities = resolvedRoles
                            .stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();

                    // Use email/phone as principal instead of username
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(emailOrPhone, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.info("✅ Authentication set successfully for user: {} with {} roles",
                            user.getUsername(), authorities.size());
                } else {
                    log.warn("❌ User not found for identifier: {}", emailOrPhone);
                }
            } catch (Exception e) {
                log.error("❌ Exception in JWT authentication filter: {}", e.getMessage(), e);
            }
        } else {
            log.debug("⚠️ No JWT token found in request");
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

