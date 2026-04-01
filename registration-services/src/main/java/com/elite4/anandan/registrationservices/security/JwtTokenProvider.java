package com.elite4.anandan.registrationservices.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/**
 * Utility component for generating and validating JWT tokens.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private final Key key;
    private final long validityInMillis;

    public JwtTokenProvider(
            @Value("${app.jwt.secret:change-me-secret-key-change-me-secret-key}") String secret,
            @Value("${app.jwt.expiration-ms:6000000}") long validityInMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityInMillis = validityInMillis;
        log.info("🔑 JWT Token Provider initialized. Expiration: {} ms ({} hours)",
                validityInMillis, validityInMillis / 3600000);
    }

    /**
     * Generate JWT token using email or phoneNumber as subject
     * Changed from username to email/phoneNumber for authentication
     */
    public String generateToken(String emailOrPhone) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityInMillis);

        log.debug("🔑 Generating token for: {}", emailOrPhone);
        log.debug("🔑 Token issued at: {}", now);
        log.debug("🔑 Token expires at: {}", expiry);
        log.debug("🔑 Validity in millis: {}", validityInMillis);

        String token = Jwts.builder()
                .setSubject(emailOrPhone)  // Changed: Now stores email or phone instead of username
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        log.info("✅ Token generated successfully for: {}. Token length: {}", emailOrPhone, token.length());
        return token;
    }

    /**
     * Extract email or phoneNumber from token (was getUsernameFromToken)
     * Changed method name to reflect new behavior
     */
    public String getEmailOrPhoneFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            String subject = claims.getSubject();
            Date expiration = claims.getExpiration();
            log.debug("✅ Token parsed successfully. Subject: {}, Expiration: {}", subject, expiration);
            return subject;  // Returns email or phoneNumber
        } catch (Exception e) {
            log.error("❌ Error parsing token: {}", e.getMessage());
            throw e;
        }
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
            log.debug("✅ Token validation successful");
            return true;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("❌ JWT token expired: {}", e.getMessage());
            return false;
        } catch (io.jsonwebtoken.UnsupportedJwtException e) {
            log.warn("❌ JWT token unsupported: {}", e.getMessage());
            return false;
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            log.warn("❌ JWT token malformed: {}", e.getMessage());
            return false;
        } catch (io.jsonwebtoken.SignatureException e) {
            log.warn("❌ JWT signature validation failed: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("❌ JWT token is empty: {}", e.getMessage());
            return false;
        } catch (Exception ex) {
            log.warn("❌ Token validation failed: {}", ex.getMessage());
            return false;
        }
    }
}

