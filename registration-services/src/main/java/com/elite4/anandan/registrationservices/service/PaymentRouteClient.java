package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.dto.BankDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for payment-services Route API.
 * Syncs bank accounts from registration to Razorpay linked accounts,
 * then triggers KYC submission and document uploads.
 */
@Slf4j
@Component
public class PaymentRouteClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PaymentRouteClient(RestTemplate restTemplate,
                              @Value("${payment.service.url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Full Razorpay onboarding flow per bank account:
     *   Step 1 → POST /payments/route/linked-accounts (creates Razorpay account + product config)
     *   Step 2 → POST /payments/route/linked-accounts/{id}/submit-kyc (stakeholder + KYC)
     *   Step 3 → POST /payments/route/linked-accounts/{id}/upload-document (KYC documents)
     *
     * Fire-and-forget — failures are logged but don't block registration.
     */
    public void syncBankAccounts(String ownerUsername, String coliveName, String email,
                                 String phone, List<BankDetails> bankDetailsList,
                                 String panNumber, String gstNumber, String legalBusinessName,
                                 String businessType, String businessAddress) {
        syncBankAccounts(ownerUsername, coliveName, email, phone, bankDetailsList,
                panNumber, gstNumber, legalBusinessName, businessType, businessAddress, null);
    }

    /**
     * Full Razorpay onboarding flow with optional KYC document uploads.
     *
     * @param kycDocuments map of documentType → file bytes (e.g. "pan" → byte[], "aadhar_front" → byte[])
     */
    public void syncBankAccounts(String ownerUsername, String coliveName, String email,
                                 String phone, List<BankDetails> bankDetailsList,
                                 String panNumber, String gstNumber, String legalBusinessName,
                                 String businessType, String businessAddress,
                                 Map<String, byte[]> kycDocuments) {
        if (bankDetailsList == null || bankDetailsList.isEmpty()) {
            log.info("No bank details to sync for owner={}, colive={}", ownerUsername, coliveName);
            return;
        }

        for (int i = 0; i < bankDetailsList.size(); i++) {
            BankDetails bd = bankDetailsList.get(i);
            try {
                // ── Step 1: Create linked account ──
                Map<String, Object> req = new HashMap<>();
                req.put("ownerUsername", ownerUsername);
                req.put("coliveName", coliveName);
                req.put("bankName", bd.getBankName());
                req.put("accountNumber", bd.getAccountNumber());
                req.put("ifscCode", bd.getIfscCode());
                req.put("beneficiaryName", bd.getAccountHolderName() != null ? bd.getAccountHolderName() : ownerUsername);
                req.put("upiId", bd.getUpiId() != null ? bd.getUpiId() : "");
                req.put("contactName", bd.getAccountHolderName() != null ? bd.getAccountHolderName() : ownerUsername);
                req.put("email", email != null ? email : "");
                req.put("phone", phone != null ? phone : "");

                if (panNumber != null && !panNumber.isBlank()) req.put("panNumber", panNumber);
                if (gstNumber != null && !gstNumber.isBlank()) req.put("gstNumber", gstNumber);
                if (legalBusinessName != null && !legalBusinessName.isBlank()) req.put("legalBusinessName", legalBusinessName);
                if (businessType != null && !businessType.isBlank()) req.put("businessType", businessType);
                if (businessAddress != null && !businessAddress.isBlank()) req.put("businessAddress", businessAddress);

                @SuppressWarnings("unchecked")
                Map<String, Object> accountResponse = restTemplate.postForObject(
                        baseUrl + "/payments/route/linked-accounts", req, Map.class);

                log.info("✅ Step 1 complete: Bank account {} synced to Route API for owner={}, colive={}, bank={}",
                        (i + 1), ownerUsername, coliveName, bd.getBankName());

                // Extract account ID from the response for Step 2 & 3
                String accountId = accountResponse != null ? (String) accountResponse.get("id") : null;

                if (accountId == null || accountId.isBlank()) {
                    log.warn("⚠️ No account ID returned from Route API — skipping KYC submission for bank account {}", (i + 1));
                    continue;
                }

                // ── Step 2: Submit KYC (creates stakeholder on Razorpay) ──
                boolean hasKycData = (panNumber != null && !panNumber.isBlank())
                        || (gstNumber != null && !gstNumber.isBlank());
                if (hasKycData) {
                    submitKyc(accountId, ownerUsername, email, phone,
                            panNumber, gstNumber, legalBusinessName, businessType, businessAddress);
                }

                // ── Step 3: Upload KYC documents ──
                if (kycDocuments != null && !kycDocuments.isEmpty()) {
                    for (Map.Entry<String, byte[]> docEntry : kycDocuments.entrySet()) {
                        uploadDocument(accountId, docEntry.getKey(), docEntry.getValue(), ownerUsername);
                    }
                }

            } catch (Exception e) {
                log.warn("⚠️ Failed to sync bank account {} to Route API for owner={}, colive={}: {}",
                        (i + 1), ownerUsername, coliveName, e.getMessage());
            }
        }
    }

    /**
     * Step 2: Submit KYC details for a linked account (creates stakeholder on Razorpay).
     */
    private void submitKyc(String accountId, String ownerUsername, String email, String phone,
                           String panNumber, String gstNumber, String legalBusinessName,
                           String businessType, String businessAddress) {
        try {
            Map<String, Object> kycReq = new HashMap<>();
            if (panNumber != null && !panNumber.isBlank()) kycReq.put("panNumber", panNumber);
            if (gstNumber != null && !gstNumber.isBlank()) kycReq.put("gstNumber", gstNumber);
            if (legalBusinessName != null && !legalBusinessName.isBlank()) kycReq.put("legalBusinessName", legalBusinessName);
            if (businessType != null && !businessType.isBlank()) kycReq.put("businessType", businessType);
            if (businessAddress != null && !businessAddress.isBlank()) kycReq.put("businessAddress", businessAddress);
            kycReq.put("stakeholderName", ownerUsername);
            if (email != null && !email.isBlank()) kycReq.put("stakeholderEmail", email);
            if (phone != null && !phone.isBlank()) kycReq.put("stakeholderPhone", phone);

            restTemplate.postForObject(
                    baseUrl + "/payments/route/linked-accounts/" + accountId + "/submit-kyc",
                    kycReq, Map.class);

            log.info("✅ Step 2 complete: KYC submitted for account={}, owner={}", accountId, ownerUsername);
        } catch (Exception e) {
            log.warn("⚠️ KYC submission failed for account={}, owner={}: {}", accountId, ownerUsername, e.getMessage());
        }
    }

    /**
     * Step 3: Upload a KYC document to Razorpay via payment-services.
     */
    private void uploadDocument(String accountId, String documentType, byte[] fileBytes, String ownerUsername) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("documentType", documentType);
            body.add("file", new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return documentType + ".jpg";
                }
            });

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForObject(
                    baseUrl + "/payments/route/linked-accounts/" + accountId + "/upload-document",
                    entity, Map.class);

            log.info("✅ Step 3: Document '{}' uploaded for account={}, owner={}", documentType, accountId, ownerUsername);
        } catch (Exception e) {
            log.warn("⚠️ Document upload failed for account={}, type={}, owner={}: {}",
                    accountId, documentType, ownerUsername, e.getMessage());
        }
    }
}
