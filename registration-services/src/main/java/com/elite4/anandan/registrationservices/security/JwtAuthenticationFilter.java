package com.elite4.anandan.registrationservices.security;


import com.elite4.anandan.registrationservices.model.Role;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.repository.RoleRepository;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

        if (jwt != null && tokenProvider.validateToken(jwt) && SecurityContextHolder.getContext().getAuthentication() == null) {
            // Get email or phoneNumber from token (not username)
            String emailOrPhone = tokenProvider.getEmailOrPhoneFromToken(jwt);

            // Find user by email or phoneNumber
            Optional<User> userOpt = Optional.empty();

            // Try to find by email first
            if (emailOrPhone.contains("@")) {
                // It's an email (contains @)
                userOpt = userRepository.findByEmail(emailOrPhone);
            } else {
                // It's a phone number
                userOpt = userRepository.findByPhoneRaw(emailOrPhone);
            }

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Set<String> resolvedRoles = new HashSet<>();
                for (String value : user.getRoleIds()) {
                    if (value == null || value.isBlank()) {
                        continue;
                    }
                    Optional<Role> savedRole  = roleRepository.findById(value);
                    if (savedRole.isPresent()) {
                        resolvedRoles.add(savedRole.get().getName().name());
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
            }
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

