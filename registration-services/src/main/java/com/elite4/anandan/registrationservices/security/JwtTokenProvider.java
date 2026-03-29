package com.elite4.anandan.registrationservices.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/**
 * Utility component for generating and validating JWT tokens.
 */
@Component
public class JwtTokenProvider {

    private final Key key;
    private final long validityInMillis;

    public JwtTokenProvider(
            @Value("${app.jwt.secret:change-me-secret-key-change-me-secret-key}") String secret,
            @Value("${app.jwt.expiration-ms:6000000}") long validityInMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityInMillis = validityInMillis;
    }

    /**
     * Generate JWT token using email or phoneNumber as subject
     * Changed from username to email/phoneNumber for authentication
     */
    public String generateToken(String emailOrPhone) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityInMillis);

        return Jwts.builder()
                .setSubject(emailOrPhone)  // Changed: Now stores email or phone instead of username
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extract email or phoneNumber from token (was getUsernameFromToken)
     * Changed method name to reflect new behavior
     */
    public String getEmailOrPhoneFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();  // Returns email or phoneNumber
    }

    /**
     * Legacy method - kept for backward compatibility
     * Now calls getEmailOrPhoneFromToken()
     */
    public String getUsernameFromToken(String token) {
        return getEmailOrPhoneFromToken(token);  // Now returns email/phone instead of username
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}

