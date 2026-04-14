package com.elite4.anandan.registrationservices.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Proxy controller for KYC document management.
 * Forwards requests from authenticated frontend to payment-services.
 */
@RestController
@RequestMapping("/adminservices/kyc")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Slf4j
public class KycController {

    private final RestTemplate restTemplate;
    private final String paymentServiceUrl;

    public KycController(RestTemplate restTemplate,
                         @Value("${payment.service.url}") String paymentServiceUrl) {
        this.restTemplate = restTemplate;
        this.paymentServiceUrl = paymentServiceUrl;
    }

    /**
     * Get all linked accounts (with KYC status) for the given owner.
     */
    @GetMapping("/linked-accounts")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> getLinkedAccounts(
            @RequestParam String ownerUsername,
            @RequestParam(required = false) String coliveName) {
        log.info("GET /adminservices/kyc/linked-accounts → owner={}, colive={}", ownerUsername, coliveName);
        try {
            String url = paymentServiceUrl + "/payments/route/linked-accounts/by-owner?ownerUsername="
                    + URLEncoder.encode(ownerUsername, StandardCharsets.UTF_8);
            if (coliveName != null && !coliveName.isBlank()) {
                url += "&coliveName=" + URLEncoder.encode(coliveName, StandardCharsets.UTF_8);
            }
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("Failed to fetch linked accounts for owner={}, colive={}: {}", ownerUsername, coliveName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch linked accounts: " + e.getMessage()));
        }
    }

    /**
     * Get supported KYC document types.
     */
    @GetMapping("/document-types")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> getDocumentTypes() {
        log.info("GET /adminservices/kyc/document-types");
        try {
            ResponseEntity<List> response = restTemplate.getForEntity(
                    paymentServiceUrl + "/payments/route/linked-accounts/document-types",
                    List.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("Failed to fetch document types: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch document types: " + e.getMessage()));
        }
    }

    /**
     * Upload a KYC document for a linked account → forwards to payment-services → Razorpay.
     */
    @PostMapping(value = "/linked-accounts/{accountId}/upload-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> uploadDocument(
            @PathVariable String accountId,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) {
        log.info("POST /adminservices/kyc/linked-accounts/{}/upload-document → type={}, file={}",
                accountId, documentType, file.getOriginalFilename());

        if (documentType == null || documentType.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "documentType is required"));
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "file is required"));
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("message", "File size exceeds 5MB limit"));
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("documentType", documentType);
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    paymentServiceUrl + "/payments/route/linked-accounts/" + accountId + "/upload-document",
                    entity, Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("Failed to upload document for account={}, type={}: {}", accountId, documentType, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to upload document: " + e.getMessage()));
        }
    }

    /**
     * Get KYC status for a specific linked account.
     */
    @GetMapping("/linked-accounts/{accountId}/kyc-status")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> getKycStatus(@PathVariable String accountId) {
        log.info("GET /adminservices/kyc/linked-accounts/{}/kyc-status", accountId);
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    paymentServiceUrl + "/payments/route/linked-accounts/" + accountId + "/kyc-status",
                    Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("Failed to fetch KYC status for account={}: {}", accountId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch KYC status: " + e.getMessage()));
        }
    }
}
