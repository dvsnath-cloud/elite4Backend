package com.elite4.anandan.registrationservices.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

/**
 * JWT provider using RSA RS256 (asymmetric signing).
 *
 * Production: configure app.jwt.rsa.private-key and app.jwt.rsa.public-key with PEM-encoded keys.
 * Development: if keys are absent, an ephemeral 2048-bit RSA key pair is generated at startup
 *              (tokens are invalidated on every restart — not suitable for multi-instance deployments).
 *
 * JWKS public endpoint: GET /adminservices/.well-known/jwks.json
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final long validityInMillis;

    public JwtTokenProvider(
            @Value("${app.jwt.rsa.private-key:}") String privateKeyPem,
            @Value("${app.jwt.rsa.public-key:}") String publicKeyPem,
            @Value("${app.jwt.expiration-ms:86400000}") long validityInMillis) throws Exception {

        this.validityInMillis = validityInMillis;

        if (privateKeyPem != null && !privateKeyPem.isBlank()) {
            this.privateKey = parseRsaPrivateKey(privateKeyPem);
            this.publicKey  = parseRsaPublicKey(publicKeyPem);
            log.info("🔑 JWT RS256 — RSA keys loaded from configuration. Expiry: {}ms", validityInMillis);
        } else {
            log.warn("⚠️  JWT RS256 — No RSA keys configured. Generating ephemeral 2048-bit key pair. " +
                     "Tokens will be invalidated on restart. Set app.jwt.rsa.private-key / app.jwt.rsa.public-key for production.");
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048, new SecureRandom());
            KeyPair kp = gen.generateKeyPair();
            this.privateKey = (RSAPrivateKey) kp.getPrivate();
            this.publicKey  = (RSAPublicKey)  kp.getPublic();
            log.info("🔑 JWT RS256 — Ephemeral key pair generated. Expiry: {}ms", validityInMillis);
        }
    }

    // ── Token generation ──────────────────────────────────────────────────────

    /** Signs a JWT with RS256, using email or phone as the subject. */
    public String generateToken(String emailOrPhone) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + validityInMillis);
        String token = Jwts.builder()
                .setSubject(emailOrPhone)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
        log.info("✅ RS256 token generated for: {}, expires: {}", emailOrPhone, expiry);
        return token;
    }

    // ── Token validation ──────────────────────────────────────────────────────

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(publicKey).build().parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("❌ JWT expired: {}", e.getMessage());
        } catch (io.jsonwebtoken.UnsupportedJwtException e) {
            log.warn("❌ JWT unsupported: {}", e.getMessage());
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            log.warn("❌ JWT malformed: {}", e.getMessage());
        } catch (io.jsonwebtoken.SignatureException e) {
            log.warn("❌ JWT signature invalid: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("❌ JWT validation failed: {}", e.getMessage());
        }
        return false;
    }

    // ── Subject extraction ────────────────────────────────────────────────────

    /** Returns the email or phone stored in the token subject. */
    public String getEmailOrPhoneFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        log.debug("✅ Token parsed. Subject: {}, Expiry: {}", claims.getSubject(), claims.getExpiration());
        return claims.getSubject();
    }

    /** Backward-compatible alias. */
    public String getUsernameFromToken(String token) {
        return getEmailOrPhoneFromToken(token);
    }

    // ── JWKS public key accessor ──────────────────────────────────────────────

    /** Exposes the RSA public key for the JWKS endpoint. */
    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    // ── PEM parsing helpers ───────────────────────────────────────────────────

    private RSAPrivateKey parseRsaPrivateKey(String pem) throws Exception {
        String stripped = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(stripped);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private RSAPublicKey parseRsaPublicKey(String pem) throws Exception {
        String stripped = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(stripped);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    }
}

