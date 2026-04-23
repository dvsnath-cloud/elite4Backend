package com.elite4.anandan.paymentservices.service;

import com.elite4.anandan.paymentservices.document.LinkedAccountDocument;
import com.elite4.anandan.paymentservices.dto.KycSubmissionRequest;
import com.elite4.anandan.paymentservices.dto.LinkedAccountRequest;
import com.elite4.anandan.paymentservices.dto.LinkedAccountResponse;
import com.elite4.anandan.paymentservices.repository.LinkedAccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LinkedAccountService {

    private final LinkedAccountRepository linkedAccountRepository;
    private final RestTemplate restTemplate;
    private final RazorpayOAuthTokenProvider tokenProvider;
    private final String keyId;
    private final String keySecret;

    private static final String RAZORPAY_BASE_URL = "https://api.razorpay.com";

    public LinkedAccountService(@Value("${razorpay.keyId}") String keyId,
                                @Value("${razorpay.keySecret}") String keySecret,
                                LinkedAccountRepository linkedAccountRepository,
                                RestTemplate restTemplate,
                                RazorpayOAuthTokenProvider tokenProvider) {
        this.linkedAccountRepository = linkedAccountRepository;
        this.restTemplate = restTemplate;
        this.tokenProvider = tokenProvider;
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

            // Include bank_account in the /v2/accounts request for settlement setup
            accountRequest.put("bank_account", bankAccount);

            JSONObject profile = new JSONObject();
            profile.put("category", "housing");
            profile.put("subcategory", "pg_and_hostels");
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
            notes.put("platform", "CoLives Connect");
            accountRequest.put("notes", notes);

            log.info("Razorpay API → POST /v2/accounts, payload keys: {}", accountRequest.keySet());

            // Call Razorpay /v2/accounts
            String razorpayAccountId;
            String activationStatus = "NEW";
            boolean razorpaySynced = false;
            String syncFailureReason = null;

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
                razorpaySynced = true;

                log.info("Razorpay account created: id={}, status={}", razorpayAccountId, activationStatus);

                // Configure product (route) for this account
                configureProductForAccount(razorpayAccountId, bankAccount);

            } catch (Exception apiEx) {
                log.error("Razorpay API call failed for owner={}, colive={}: {}",
                        request.getOwnerUsername(), request.getColiveName(), apiEx.getMessage(), apiEx);
                // Save with placeholder ID — will be retried by scheduled sync job
                razorpayAccountId = "acc_pending_" + System.currentTimeMillis();
                activationStatus = "PENDING_SYNC";
                syncFailureReason = apiEx.getMessage();
                log.warn("Saved with pending sync placeholder: {}. Will retry via scheduled job.", razorpayAccountId);
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
            doc.setRazorpaySynced(razorpaySynced);
            doc.setSyncFailureReason(syncFailureReason);
            doc.setPrimary(shouldBePrimary);
            doc.setStatus(razorpaySynced ? "ACTIVE" : "PENDING_SYNC");
            doc.setCreatedAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());

            LinkedAccountDocument saved = linkedAccountRepository.save(doc);
            log.info("Linked account created: id={}, razorpayAccountId={}, primary={}, razorpaySynced={}",
                    saved.getId(), saved.getRazorpayAccountId(), saved.isPrimary(), saved.isRazorpaySynced());

            String message = razorpaySynced
                    ? "Bank account added successfully." + (shouldBePrimary ? " Set as primary." : "")
                    : "Bank account saved locally. Razorpay sync pending — will retry automatically.";
            return toResponse(saved, message);

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
            log.error("Stakeholder creation failed for account {}: {}",
                    razorpayAccountId, e.getMessage(), e);
            throw new RuntimeException("Failed to create stakeholder on Razorpay: " + e.getMessage(), e);
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
     * Phase 2: Upload KYC document to Razorpay for a linked account's stakeholder
     * Razorpay API: POST /v2/accounts/{account_id}/stakeholders/{stakeholder_id}/documents
     */
    public LinkedAccountResponse uploadDocument(String accountId, String documentType, MultipartFile file) {
        LinkedAccountDocument doc = linkedAccountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Linked account not found: " + accountId));

        if (doc.getRazorpayAccountId() == null || doc.getRazorpayAccountId().isBlank()) {
            throw new RuntimeException("No Razorpay account ID found. Create account first.");
        }
        if (doc.getStakeholderId() == null || doc.getStakeholderId().isBlank()) {
            throw new RuntimeException("No stakeholder ID found. Submit KYC first to create a stakeholder.");
        }

        log.info("Uploading document type={} for account id={}, razorpayId={}, stakeholderId={}, file={}",
                documentType, accountId, doc.getRazorpayAccountId(), doc.getStakeholderId(), file.getOriginalFilename());

        try {
            String razorpayDocId = uploadDocumentToRazorpay(
                    doc.getRazorpayAccountId(), doc.getStakeholderId(), documentType, file);

            // Track the uploaded document locally
            LinkedAccountDocument.UploadedDocument uploadedDoc = new LinkedAccountDocument.UploadedDocument();
            uploadedDoc.setDocumentType(documentType);
            uploadedDoc.setRazorpayDocId(razorpayDocId);
            uploadedDoc.setOriginalFileName(file.getOriginalFilename());
            uploadedDoc.setUploadedAt(LocalDateTime.now());

            if (doc.getUploadedDocuments() == null) {
                doc.setUploadedDocuments(new java.util.ArrayList<>());
            }
            // Replace existing document of the same type (re-upload scenario)
            doc.getUploadedDocuments().removeIf(d -> documentType.equals(d.getDocumentType()));
            doc.getUploadedDocuments().add(uploadedDoc);
            doc.setUpdatedAt(LocalDateTime.now());

            LinkedAccountDocument saved = linkedAccountRepository.save(doc);
            log.info("Document uploaded: type={}, razorpayDocId={} for account {}", documentType, razorpayDocId, accountId);

            return toResponse(saved, "Document '" + documentType + "' uploaded successfully.");

        } catch (Exception e) {
            log.error("Document upload failed for account {}: {}", accountId, e.getMessage(), e);
            return toResponse(doc, "Document upload failed: " + e.getMessage());
        }
    }

    /**
     * Call Razorpay API to upload a document file
     */
    private String uploadDocumentToRazorpay(String razorpayAccountId, String stakeholderId,
                                            String documentType, MultipartFile file) {
        try {
            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("document[type]", documentType);
            body.add("file", new org.springframework.core.io.ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            String url = RAZORPAY_BASE_URL + "/v2/accounts/" + razorpayAccountId
                    + "/stakeholders/" + stakeholderId + "/documents";

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            JSONObject responseBody = new JSONObject(response.getBody());
            // Razorpay returns array of documents; the last one is the newly uploaded
            if (responseBody.has("document")) {
                JSONArray documents = responseBody.getJSONArray("document");
                if (documents.length() > 0) {
                    return documents.getJSONObject(documents.length() - 1).optString("id", "doc_" + System.currentTimeMillis());
                }
            }
            return "doc_" + System.currentTimeMillis();

        } catch (Exception e) {
            log.error("Razorpay document upload API failed for account={}, stakeholder={}, type={}: {}",
                    razorpayAccountId, stakeholderId, documentType, e.getMessage(), e);
            throw new RuntimeException("Failed to upload document to Razorpay: " + e.getMessage(), e);
        }
    }

    /**
     * Get list of supported document types for Razorpay KYC
     */
    public List<String> getSupportedDocumentTypes() {
        return List.of(
                "aadhar_front", "aadhar_back",
                "pan",
                "passport_front", "passport_back",
                "voter_id_front", "voter_id_back",
                "driving_license_front", "driving_license_back",
                "business_proof_url",
                "cancelled_cheque",
                "gst_certificate",
                "shop_establishment_certificate"
        );
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

    public List<LinkedAccountDocument> getAccountsByOwner(String ownerUsername) {
        return linkedAccountRepository.findByOwnerUsername(ownerUsername);
    }

    public Optional<LinkedAccountDocument> getPrimaryAccount(String ownerUsername, String coliveName) {
        Optional<LinkedAccountDocument> primary = linkedAccountRepository
                .findByOwnerUsernameAndColiveNameAndPrimaryTrue(ownerUsername, coliveName);

        // If primary exists and is synced with Razorpay, return it
        if (primary.isPresent() && primary.get().isRazorpaySynced()) {
            return primary;
        }

        // If primary exists but is NOT synced, try to find any synced account for this owner+colive
        List<LinkedAccountDocument> allAccounts = linkedAccountRepository
                .findByOwnerUsernameAndColiveName(ownerUsername, coliveName);

        Optional<LinkedAccountDocument> syncedAccount = allAccounts.stream()
                .filter(LinkedAccountDocument::isRazorpaySynced)
                .findFirst();

        if (syncedAccount.isPresent()) {
            log.info("Primary account is unsynced. Returning first synced account {} for owner={}, colive={}",
                    syncedAccount.get().getId(), ownerUsername, coliveName);
            return syncedAccount;
        }

        // No synced accounts found — return the primary anyway (UI will show sync status)
        return primary;
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

    /**
     * Retry Razorpay sync for all unsynced accounts (razorpaySynced=false).
     * Called by the scheduled job or manually via API.
     */
    public int retryUnsyncedAccounts() {
        List<LinkedAccountDocument> unsyncedAccounts = linkedAccountRepository.findByRazorpaySyncedFalse();
        if (unsyncedAccounts.isEmpty()) {
            log.info("No unsynced linked accounts found. Nothing to retry.");
            return 0;
        }

        log.info("Found {} unsynced linked accounts. Starting retry...", unsyncedAccounts.size());
        int successCount = 0;

        for (LinkedAccountDocument doc : unsyncedAccounts) {
            try {
                // Rebuild the Razorpay /v2/accounts request from stored fields
                JSONObject accountRequest = new JSONObject();
                accountRequest.put("email", doc.getEmail());
                accountRequest.put("phone", doc.getPhone());
                accountRequest.put("type", "route");
                accountRequest.put("legal_business_name", doc.getLegalBusinessName() != null
                        ? doc.getLegalBusinessName() : doc.getContactName());
                accountRequest.put("business_type", doc.getBusinessType() != null
                        ? doc.getBusinessType() : "individual");
                accountRequest.put("contact_name", doc.getContactName());

                // Legal info
                JSONObject legalInfo = new JSONObject();
                if (doc.getPanNumber() != null && !doc.getPanNumber().isBlank()) {
                    legalInfo.put("pan", doc.getPanNumber());
                }
                if (doc.getGstNumber() != null && !doc.getGstNumber().isBlank()) {
                    legalInfo.put("gst", doc.getGstNumber());
                }
                if (legalInfo.length() > 0) {
                    accountRequest.put("legal_info", legalInfo);
                }

                // Bank account
                JSONObject bankAccount = new JSONObject();
                bankAccount.put("ifsc_code", doc.getIfscCode());
                bankAccount.put("beneficiary_name", doc.getBeneficiaryName());
                bankAccount.put("account_number", doc.getAccountNumber());
                bankAccount.put("account_type", "current");
                accountRequest.put("bank_account", bankAccount);

                // Profile
                JSONObject profile = new JSONObject();
                profile.put("category", "housing");
                profile.put("subcategory", "pg_and_hostels");
                if (doc.getBusinessAddress() != null) {
                    JSONObject addresses = new JSONObject();
                    JSONObject registered = new JSONObject();
                    registered.put("street1", doc.getBusinessAddress());
                    registered.put("city", "NA");
                    registered.put("state", "NA");
                    registered.put("postal_code", "000000");
                    registered.put("country", "IN");
                    addresses.put("registered", registered);
                    profile.put("addresses", addresses);
                }
                accountRequest.put("profile", profile);

                JSONObject notes = new JSONObject();
                notes.put("ownerUsername", doc.getOwnerUsername());
                notes.put("coliveName", doc.getColiveName());
                notes.put("platform", "CoLives Connect");
                accountRequest.put("notes", notes);

                // Call Razorpay
                HttpHeaders headers = createAuthHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> entity = new HttpEntity<>(accountRequest.toString(), headers);

                ResponseEntity<String> response = restTemplate.exchange(
                        RAZORPAY_BASE_URL + "/v2/accounts",
                        HttpMethod.POST, entity, String.class);

                JSONObject responseBody = new JSONObject(response.getBody());
                String razorpayAccountId = responseBody.getString("id");
                String activationStatus = responseBody.optString("status", "created");

                // Update the document with real Razorpay data
                doc.setRazorpayAccountId(razorpayAccountId);
                doc.setActivationStatus(activationStatus);
                doc.setRazorpaySynced(true);
                doc.setSyncFailureReason(null);
                doc.setStatus("ACTIVE");
                doc.setUpdatedAt(LocalDateTime.now());
                linkedAccountRepository.save(doc);

                log.info("Retry SUCCESS: account {} synced to Razorpay as {}", doc.getId(), razorpayAccountId);

                // Configure product
                configureProductForAccount(razorpayAccountId, bankAccount);
                successCount++;

            } catch (Exception e) {
                log.warn("Retry FAILED for account {} (owner={}, colive={}): {}",
                        doc.getId(), doc.getOwnerUsername(), doc.getColiveName(), e.getMessage());
                doc.setSyncFailureReason(e.getMessage());
                doc.setUpdatedAt(LocalDateTime.now());
                linkedAccountRepository.save(doc);
            }
        }

        log.info("Retry complete: {}/{} accounts synced successfully.", successCount, unsyncedAccounts.size());
        return successCount;
    }

    /**
     * Get all unsynced accounts for monitoring/dashboard.
     */
    public List<LinkedAccountDocument> getUnsyncedAccounts() {
        return linkedAccountRepository.findByRazorpaySyncedFalse();
    }

    private LinkedAccountResponse toResponse(LinkedAccountDocument doc, String message) {
        List<String> uploadedDocTypes = doc.getUploadedDocuments() != null
                ? doc.getUploadedDocuments().stream()
                    .map(LinkedAccountDocument.UploadedDocument::getDocumentType)
                    .toList()
                : List.of();

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
                .razorpaySynced(doc.isRazorpaySynced())
                .syncFailureReason(doc.getSyncFailureReason())
                .uploadedDocumentTypes(uploadedDocTypes)
                .message(message)
                .build();
    }

    private HttpHeaders createAuthHeaders() {
        return tokenProvider.createAuthHeaders(keyId, keySecret);
    }

    private String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) return "****";
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}
