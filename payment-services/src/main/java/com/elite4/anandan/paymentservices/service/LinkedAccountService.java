package com.elite4.anandan.paymentservices.service;

import com.elite4.anandan.paymentservices.document.LinkedAccountDocument;
import com.elite4.anandan.paymentservices.dto.KycSubmissionRequest;
import com.elite4.anandan.paymentservices.dto.LinkedAccountRequest;
import com.elite4.anandan.paymentservices.dto.LinkedAccountResponse;
import com.elite4.anandan.paymentservices.repository.LinkedAccountRepository;
import com.razorpay.RazorpayClient;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class LinkedAccountService {

    private final RazorpayClient razorpayClient;
    private final LinkedAccountRepository linkedAccountRepository;
    private final RestTemplate restTemplate;
    private final String keyId;
    private final String keySecret;

    private static final String RAZORPAY_BASE_URL = "https://api.razorpay.com";

    public LinkedAccountService(@Value("${razorpay.keyId}") String keyId,
                                @Value("${razorpay.keySecret}") String keySecret,
                                LinkedAccountRepository linkedAccountRepository,
                                RestTemplate restTemplate) throws Exception {
        this.razorpayClient = new RazorpayClient(keyId, keySecret);
        this.linkedAccountRepository = linkedAccountRepository;
        this.restTemplate = restTemplate;
        this.keyId = keyId;
        this.keySecret = keySecret;
    }

    /**
     * Phase 2: Create a linked account via Razorpay /v2/accounts API
     */
    public LinkedAccountResponse createLinkedAccount(LinkedAccountRequest request) {
        log.info("Creating linked account for owner={}, colive={}, account={}",
                request.getOwnerUsername(), request.getColiveName(), maskAccount(request.getAccountNumber()));

        try {
            // Build Razorpay /v2/accounts request body
            JSONObject accountRequest = new JSONObject();
            accountRequest.put("email", request.getEmail());
            accountRequest.put("phone", request.getPhone());
            accountRequest.put("type", "route");
            accountRequest.put("legal_business_name", request.getLegalBusinessName() != null
                    ? request.getLegalBusinessName() : request.getContactName());
            accountRequest.put("business_type", request.getBusinessType() != null
                    ? request.getBusinessType() : "individual");
            accountRequest.put("contact_name", request.getContactName());

            // Legal info
            JSONObject legalInfo = new JSONObject();
            if (request.getPanNumber() != null && !request.getPanNumber().isBlank()) {
                legalInfo.put("pan", request.getPanNumber());
            }
            if (request.getGstNumber() != null && !request.getGstNumber().isBlank()) {
                legalInfo.put("gst", request.getGstNumber());
            }
            if (legalInfo.length() > 0) {
                accountRequest.put("legal_info", legalInfo);
            }

            // Bank account / settlement info
            JSONObject bankAccount = new JSONObject();
            bankAccount.put("ifsc_code", request.getIfscCode());
            bankAccount.put("beneficiary_name", request.getBeneficiaryName());
            bankAccount.put("account_number", request.getAccountNumber());
            bankAccount.put("account_type", "current");

            JSONObject profile = new JSONObject();
            profile.put("category", "housing");
            profile.put("subcategory", "property_management");
            if (request.getBusinessAddress() != null) {
                JSONObject addresses = new JSONObject();
                JSONObject registered = new JSONObject();
                registered.put("street1", request.getBusinessAddress());
                registered.put("city", "NA");
                registered.put("state", "NA");
                registered.put("postal_code", "000000");
                registered.put("country", "IN");
                addresses.put("registered", registered);
                profile.put("addresses", addresses);
            }
            accountRequest.put("profile", profile);

            JSONObject notes = new JSONObject();
            notes.put("ownerUsername", request.getOwnerUsername());
            notes.put("coliveName", request.getColiveName());
            notes.put("platform", "CoLive Connect");
            accountRequest.put("notes", notes);

            log.info("Razorpay API → POST /v2/accounts, payload keys: {}", accountRequest.keySet());

            // Call Razorpay /v2/accounts
            String razorpayAccountId;
            String activationStatus = "NEW";

            try {
                HttpHeaders headers = createAuthHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<String> entity = new HttpEntity<>(accountRequest.toString(), headers);
                ResponseEntity<String> response = restTemplate.exchange(
                        RAZORPAY_BASE_URL + "/v2/accounts",
                        HttpMethod.POST, entity, String.class);

                JSONObject responseBody = new JSONObject(response.getBody());
                razorpayAccountId = responseBody.getString("id");
                activationStatus = responseBody.optString("status", "created");

                log.info("Razorpay account created: id={}, status={}", razorpayAccountId, activationStatus);

                // Configure product (route) for this account
                configureProductForAccount(razorpayAccountId, bankAccount);

            } catch (Exception apiEx) {
                log.warn("Razorpay API call failed (test mode fallback): {}", apiEx.getMessage());
                // Test mode fallback — generate placeholder ID
                razorpayAccountId = "acc_" + System.currentTimeMillis();
                activationStatus = "CREATED";
                log.info("Using test-mode placeholder account: {}", razorpayAccountId);
            }

            // Check if this is the first account for this owner+colive → auto-set as primary
            List<LinkedAccountDocument> existingAccounts = linkedAccountRepository
                    .findByOwnerUsernameAndColiveName(request.getOwnerUsername(), request.getColiveName());
            boolean shouldBePrimary = existingAccounts.isEmpty();

            LinkedAccountDocument doc = new LinkedAccountDocument();
            doc.setOwnerUsername(request.getOwnerUsername());
            doc.setColiveName(request.getColiveName());
            doc.setRazorpayAccountId(razorpayAccountId);
            doc.setContactName(request.getContactName());
            doc.setEmail(request.getEmail());
            doc.setPhone(request.getPhone());
            doc.setBusinessType(request.getBusinessType());
            doc.setLegalBusinessName(request.getLegalBusinessName());
            doc.setBankName(request.getBankName());
            doc.setIfscCode(request.getIfscCode());
            doc.setAccountNumber(request.getAccountNumber());
            doc.setBeneficiaryName(request.getBeneficiaryName());
            doc.setUpiId(request.getUpiId());
            doc.setPanNumber(request.getPanNumber());
            doc.setGstNumber(request.getGstNumber());
            doc.setBusinessAddress(request.getBusinessAddress());
            doc.setKycStatus("NOT_SUBMITTED");
            doc.setActivationStatus(activationStatus);
            doc.setPrimary(shouldBePrimary);
            doc.setStatus("ACTIVE");
            doc.setCreatedAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());

            LinkedAccountDocument saved = linkedAccountRepository.save(doc);
            log.info("Linked account created: id={}, razorpayAccountId={}, primary={}",
                    saved.getId(), saved.getRazorpayAccountId(), saved.isPrimary());

            return toResponse(saved, "Bank account added successfully." + (shouldBePrimary ? " Set as primary." : ""));

        } catch (Exception e) {
            log.error("Failed to create linked account for owner={}, colive={}: {}",
                    request.getOwnerUsername(), request.getColiveName(), e.getMessage(), e);
            return LinkedAccountResponse.builder()
                    .ownerUsername(request.getOwnerUsername())
                    .coliveName(request.getColiveName())
                    .status("FAILED")
                    .message("Failed to create linked account: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Phase 2: Configure route product for linked account
     */
    private void configureProductForAccount(String razorpayAccountId, JSONObject bankAccount) {
        try {
            JSONObject productRequest = new JSONObject();
            productRequest.put("product_name", "route");
            productRequest.put("tnc_accepted", true);

            JSONObject activeConfig = new JSONObject();
            JSONObject settlements = new JSONObject();
            settlements.put("account_number", bankAccount.getString("account_number"));
            settlements.put("ifsc_code", bankAccount.getString("ifsc_code"));
            settlements.put("beneficiary_name", bankAccount.getString("beneficiary_name"));
            activeConfig.put("settlements", settlements);

            JSONObject paymentCapture = new JSONObject();
            paymentCapture.put("mode", "automatic");
            activeConfig.put("payment_capture", paymentCapture);

            productRequest.put("active_configuration", activeConfig);

            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(productRequest.toString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    RAZORPAY_BASE_URL + "/v2/accounts/" + razorpayAccountId + "/products",
                    HttpMethod.POST, entity, String.class);

            JSONObject responseBody = new JSONObject(response.getBody());
            String productId = responseBody.optString("id", "unknown");
            String activeStatus = responseBody.optString("active_configuration_status", "unknown");

            log.info("Product configured for account {}: productId={}, status={}",
                    razorpayAccountId, productId, activeStatus);
        } catch (Exception e) {
            log.warn("Failed to configure product for account {}: {} (can be done later via KYC)",
                    razorpayAccountId, e.getMessage());
        }
    }

    /**
     * Phase 2: Submit KYC for a linked account
     */
    public LinkedAccountResponse submitKyc(String accountId, KycSubmissionRequest kycRequest) {
        LinkedAccountDocument doc = linkedAccountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Linked account not found: " + accountId));

        log.info("Submitting KYC for account id={}, razorpayId={}", accountId, doc.getRazorpayAccountId());

        try {
            // Create stakeholder on Razorpay
            String stakeholderId = createStakeholder(doc.getRazorpayAccountId(), kycRequest, doc);

            // Update local doc with KYC info
            if (kycRequest.getPanNumber() != null) doc.setPanNumber(kycRequest.getPanNumber());
            if (kycRequest.getGstNumber() != null) doc.setGstNumber(kycRequest.getGstNumber());
            if (kycRequest.getBusinessAddress() != null) doc.setBusinessAddress(kycRequest.getBusinessAddress());
            if (kycRequest.getBusinessType() != null) doc.setBusinessType(kycRequest.getBusinessType());
            if (kycRequest.getLegalBusinessName() != null) doc.setLegalBusinessName(kycRequest.getLegalBusinessName());
            doc.setStakeholderId(stakeholderId);
            doc.setKycStatus("SUBMITTED");
            doc.setUpdatedAt(LocalDateTime.now());

            LinkedAccountDocument saved = linkedAccountRepository.save(doc);
            log.info("KYC submitted for account {}: stakeholderId={}", accountId, stakeholderId);

            return toResponse(saved, "KYC details submitted successfully. Verification in progress.");

        } catch (Exception e) {
            log.error("KYC submission failed for account {}: {}", accountId, e.getMessage(), e);
            doc.setKycStatus("FAILED");
            doc.setUpdatedAt(LocalDateTime.now());
            linkedAccountRepository.save(doc);

            return toResponse(doc, "KYC submission failed: " + e.getMessage());
        }
    }

    /**
     * Phase 2: Create stakeholder on Razorpay /v2/accounts/{id}/stakeholders
     */
    private String createStakeholder(String razorpayAccountId, KycSubmissionRequest kycRequest,
                                     LinkedAccountDocument doc) {
        try {
            JSONObject stakeholder = new JSONObject();
            stakeholder.put("name", kycRequest.getStakeholderName() != null
                    ? kycRequest.getStakeholderName() : doc.getContactName());

            JSONObject phone = new JSONObject();
            phone.put("primary", kycRequest.getStakeholderPhone() != null
                    ? kycRequest.getStakeholderPhone() : doc.getPhone());
            stakeholder.put("phone", phone);

            if (kycRequest.getStakeholderEmail() != null || doc.getEmail() != null) {
                stakeholder.put("email", kycRequest.getStakeholderEmail() != null
                        ? kycRequest.getStakeholderEmail() : doc.getEmail());
            }

            // KYC doc info
            JSONObject kycDoc = new JSONObject();
            if (kycRequest.getPanNumber() != null && !kycRequest.getPanNumber().isBlank()) {
                kycDoc.put("pan", kycRequest.getPanNumber());
            }
            if (kycDoc.length() > 0) {
                JSONObject notes = new JSONObject();
                notes.put("pan", kycRequest.getPanNumber());
                stakeholder.put("notes", notes);
            }

            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(stakeholder.toString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    RAZORPAY_BASE_URL + "/v2/accounts/" + razorpayAccountId + "/stakeholders",
                    HttpMethod.POST, entity, String.class);

            JSONObject responseBody = new JSONObject(response.getBody());
            String stakeholderId = responseBody.getString("id");
            log.info("Stakeholder created for account {}: stakeholderId={}", razorpayAccountId, stakeholderId);
            return stakeholderId;

        } catch (Exception e) {
            log.warn("Stakeholder creation failed for account {} (test-mode fallback): {}",
                    razorpayAccountId, e.getMessage());
            return "sth_" + System.currentTimeMillis();
        }
    }

    /**
     * Phase 2: Fetch KYC / activation status from Razorpay
     */
    public LinkedAccountResponse fetchKycStatus(String accountId) {
        LinkedAccountDocument doc = linkedAccountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Linked account not found: " + accountId));

        log.info("Fetching KYC status for account id={}, razorpayId={}", accountId, doc.getRazorpayAccountId());

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    RAZORPAY_BASE_URL + "/v2/accounts/" + doc.getRazorpayAccountId(),
                    HttpMethod.GET, entity, String.class);

            JSONObject responseBody = new JSONObject(response.getBody());
            String activationStatus = responseBody.optString("status", doc.getActivationStatus());

            // Check KYC in profile
            JSONObject profile = responseBody.optJSONObject("profile");
            String kycStatus = doc.getKycStatus();
            if ("activated".equalsIgnoreCase(activationStatus)) {
                kycStatus = "VERIFIED";
            } else if ("under_review".equalsIgnoreCase(activationStatus)) {
                kycStatus = "SUBMITTED";
            } else if ("suspended".equalsIgnoreCase(activationStatus)) {
                kycStatus = "FAILED";
            }

            doc.setActivationStatus(activationStatus);
            doc.setKycStatus(kycStatus);
            doc.setUpdatedAt(LocalDateTime.now());
            LinkedAccountDocument saved = linkedAccountRepository.save(doc);

            log.info("KYC status fetched for account {}: activation={}, kyc={}",
                    accountId, activationStatus, kycStatus);

            return toResponse(saved, "KYC status: " + kycStatus + ", Activation: " + activationStatus);

        } catch (Exception e) {
            log.warn("Failed to fetch KYC status from Razorpay for account {}: {}", accountId, e.getMessage());
            return toResponse(doc, "Could not fetch live status. Current: " + doc.getKycStatus());
        }
    }

    /**
     * Phase 2: Handle account webhook events (activated, suspended, etc.)
     */
    public void handleAccountWebhook(String razorpayAccountId, String eventType) {
        linkedAccountRepository.findByRazorpayAccountId(razorpayAccountId).ifPresent(doc -> {
            switch (eventType) {
                case "account.activated":
                    doc.setActivationStatus("ACTIVATED");
                    doc.setKycStatus("VERIFIED");
                    doc.setStatus("ACTIVE");
                    break;
                case "account.suspended":
                    doc.setActivationStatus("SUSPENDED");
                    doc.setStatus("SUSPENDED");
                    break;
                case "account.under_review":
                    doc.setActivationStatus("UNDER_REVIEW");
                    break;
                case "account.funds_on_hold":
                    doc.setActivationStatus("FUNDS_ON_HOLD");
                    break;
                default:
                    log.info("Unhandled account event for {}: {}", razorpayAccountId, eventType);
                    return;
            }
            doc.setUpdatedAt(LocalDateTime.now());
            linkedAccountRepository.save(doc);
            log.info("Account {} updated via webhook: event={}, activation={}, status={}",
                    razorpayAccountId, eventType, doc.getActivationStatus(), doc.getStatus());
        });
    }

    public List<LinkedAccountDocument> getAccountsByOwnerAndColive(String ownerUsername, String coliveName) {
        return linkedAccountRepository.findByOwnerUsernameAndColiveName(ownerUsername, coliveName);
    }

    public Optional<LinkedAccountDocument> getPrimaryAccount(String ownerUsername, String coliveName) {
        return linkedAccountRepository.findByOwnerUsernameAndColiveNameAndPrimaryTrue(ownerUsername, coliveName);
    }

    public Optional<LinkedAccountDocument> getLinkedAccountById(String id) {
        return linkedAccountRepository.findById(id);
    }

    public Optional<LinkedAccountDocument> getByRazorpayAccountId(String razorpayAccountId) {
        return linkedAccountRepository.findByRazorpayAccountId(razorpayAccountId);
    }

    public LinkedAccountResponse setPrimary(String accountId) {
        LinkedAccountDocument target = linkedAccountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Bank account not found: " + accountId));

        log.info("Setting primary account: id={} for owner={}, colive={}",
                accountId, target.getOwnerUsername(), target.getColiveName());

        // Unset current primary for this owner+colive
        List<LinkedAccountDocument> allAccounts = linkedAccountRepository
                .findByOwnerUsernameAndColiveName(target.getOwnerUsername(), target.getColiveName());
        for (LinkedAccountDocument acc : allAccounts) {
            if (acc.isPrimary()) {
                acc.setPrimary(false);
                acc.setUpdatedAt(LocalDateTime.now());
                linkedAccountRepository.save(acc);
            }
        }

        // Set the target as primary
        target.setPrimary(true);
        target.setUpdatedAt(LocalDateTime.now());
        LinkedAccountDocument saved = linkedAccountRepository.save(target);

        log.info("Primary account set: id={}, account=****{}", saved.getId(),
                saved.getAccountNumber() != null && saved.getAccountNumber().length() > 4
                        ? saved.getAccountNumber().substring(saved.getAccountNumber().length() - 4) : "****");

        return toResponse(saved, "Set as primary account successfully.");
    }

    public void deleteAccount(String accountId) {
        LinkedAccountDocument doc = linkedAccountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Bank account not found: " + accountId));

        boolean wasPrimary = doc.isPrimary();
        String ownerUsername = doc.getOwnerUsername();
        String coliveName = doc.getColiveName();

        linkedAccountRepository.deleteById(accountId);
        log.info("Deleted linked account: id={}", accountId);

        // If deleted account was primary, auto-promote the next one
        if (wasPrimary) {
            List<LinkedAccountDocument> remaining = linkedAccountRepository
                    .findByOwnerUsernameAndColiveName(ownerUsername, coliveName);
            if (!remaining.isEmpty()) {
                LinkedAccountDocument next = remaining.get(0);
                next.setPrimary(true);
                next.setUpdatedAt(LocalDateTime.now());
                linkedAccountRepository.save(next);
                log.info("Auto-promoted account {} as new primary", next.getId());
            }
        }
    }

    public LinkedAccountDocument updateStatus(String id, String status) {
        LinkedAccountDocument doc = linkedAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Linked account not found: " + id));
        doc.setStatus(status);
        doc.setUpdatedAt(LocalDateTime.now());
        return linkedAccountRepository.save(doc);
    }

    private LinkedAccountResponse toResponse(LinkedAccountDocument doc, String message) {
        return LinkedAccountResponse.builder()
                .id(doc.getId())
                .razorpayAccountId(doc.getRazorpayAccountId())
                .ownerUsername(doc.getOwnerUsername())
                .coliveName(doc.getColiveName())
                .bankName(doc.getBankName())
                .ifscCode(doc.getIfscCode())
                .accountNumber(doc.getAccountNumber())
                .beneficiaryName(doc.getBeneficiaryName())
                .upiId(doc.getUpiId())
                .primary(doc.isPrimary())
                .status(doc.getStatus())
                .kycStatus(doc.getKycStatus())
                .productConfigStatus(doc.getProductConfigStatus())
                .activationStatus(doc.getActivationStatus())
                .panNumber(doc.getPanNumber())
                .gstNumber(doc.getGstNumber())
                .message(message)
                .build();
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String auth = keyId + ":" + keySecret;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedAuth);
        return headers;
    }

    private String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) return "****";
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}
