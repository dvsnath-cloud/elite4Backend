package com.elite4.anandan.paymentservices.service;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Centralized OAuth token provider for Razorpay Partner API.
 * Obtains and caches access tokens using client_credentials grant.
 * All services needing Razorpay OAuth should use this provider.
 */
@Slf4j
@Component
public class RazorpayOAuthTokenProvider {

    private static final String RAZORPAY_AUTH_URL = "https://auth.razorpay.com/token";

    private final RestTemplate restTemplate;
    private final String clientId;
    private final String clientSecret;
    private final String mode;

    private String cachedAccessToken;
    private Instant tokenExpiresAt;

    public RazorpayOAuthTokenProvider(RestTemplate restTemplate,
                                      @Value("${razorpay.oauth.clientId}") String clientId,
                                      @Value("${razorpay.oauth.clientSecret}") String clientSecret,
                                      @Value("${razorpay.oauth.mode}") String mode) {
        this.restTemplate = restTemplate;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.mode = mode;
    }

    /**
     * Returns a valid OAuth access token, fetching a new one if expired.
     * Thread-safe via synchronization.
     *
     * @return access token string, or null if token acquisition fails
     */
    public synchronized String getAccessToken() {
        if (cachedAccessToken != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedAccessToken;
        }

        try {
            log.info("Requesting new OAuth access token from Razorpay (mode={})", mode);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String clientAuth = clientId + ":" + clientSecret;
            String encodedClientAuth = Base64.getEncoder().encodeToString(clientAuth.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + encodedClientAuth);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("mode", mode);

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    RAZORPAY_AUTH_URL, HttpMethod.POST, entity, String.class);

            JSONObject tokenResponse = new JSONObject(response.getBody());
            cachedAccessToken = tokenResponse.getString("access_token");
            int expiresIn = tokenResponse.optInt("expires_in", 3600);

            // Cache with 60-second buffer before actual expiry
            tokenExpiresAt = Instant.now().plusSeconds(expiresIn - 60);

            log.info("OAuth access token acquired successfully, expires in {}s", expiresIn);
            return cachedAccessToken;

        } catch (Exception e) {
            log.error("Failed to obtain OAuth access token from Razorpay: {}", e.getMessage(), e);
            cachedAccessToken = null;
            tokenExpiresAt = null;
            return null;
        }
    }

    /**
     * Creates HTTP Authorization headers with OAuth Bearer token.
     * Falls back to Basic Auth (keyId:keySecret) if OAuth fails.
     */
    public HttpHeaders createAuthHeaders(String keyId, String keySecret) {
        HttpHeaders headers = new HttpHeaders();
        String accessToken = getAccessToken();
        if (accessToken != null) {
            headers.set("Authorization", "Bearer " + accessToken);
        } else {
            log.warn("OAuth token unavailable, falling back to Basic Auth");
            String auth = keyId + ":" + keySecret;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + encodedAuth);
        }
        return headers;
    }

    /**
     * Invalidates the cached token (e.g., after a 401 response).
     */
    public synchronized void invalidateToken() {
        cachedAccessToken = null;
        tokenExpiresAt = null;
        log.info("OAuth token cache invalidated");
    }

    public String getMode() {
        return mode;
    }
}
