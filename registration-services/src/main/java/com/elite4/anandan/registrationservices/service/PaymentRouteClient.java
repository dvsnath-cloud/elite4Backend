package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.dto.BankDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for payment-services Route API.
 * Syncs bank accounts from registration to Razorpay linked accounts.
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
     * Sync bank details to Route API as linked accounts.
     * The first account created auto-becomes PRIMARY in Route API.
     * This is fire-and-forget — failures are logged but don't block registration.
     */
    public void syncBankAccounts(String ownerUsername, String coliveName, String email,
                                 String phone, List<BankDetails> bankDetailsList,
                                 String panNumber, String gstNumber, String legalBusinessName,
                                 String businessType, String businessAddress) {
        // Use bankDetailsList directly
        if (bankDetailsList == null || bankDetailsList.isEmpty()) {
            log.info("No bank details to sync for owner={}, colive={}", ownerUsername, coliveName);
            return;
        }

        for (int i = 0; i < bankDetailsList.size(); i++) {
            BankDetails bd = bankDetailsList.get(i);
            try {
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

                // KYC / Business details for Razorpay onboarding
                if (panNumber != null && !panNumber.isBlank()) req.put("panNumber", panNumber);
                if (gstNumber != null && !gstNumber.isBlank()) req.put("gstNumber", gstNumber);
                if (legalBusinessName != null && !legalBusinessName.isBlank()) req.put("legalBusinessName", legalBusinessName);
                if (businessType != null && !businessType.isBlank()) req.put("businessType", businessType);
                if (businessAddress != null && !businessAddress.isBlank()) req.put("businessAddress", businessAddress);

                restTemplate.postForObject(baseUrl + "/payments/route/linked-accounts", req, Map.class);
                log.info("✅ Bank account {} synced to Route API for owner={}, colive={}, bank={}",
                        (i + 1), ownerUsername, coliveName, bd.getBankName());
            } catch (Exception e) {
                log.warn("⚠️ Failed to sync bank account {} to Route API for owner={}, colive={}: {}",
                        (i + 1), ownerUsername, coliveName, e.getMessage());
                // Don't fail registration if Route API is down
            }
        }
    }
}
