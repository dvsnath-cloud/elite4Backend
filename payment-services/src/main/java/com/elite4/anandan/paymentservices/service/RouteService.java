package com.elite4.anandan.paymentservices.service;

import com.elite4.anandan.paymentservices.document.LinkedAccountDocument;
import com.elite4.anandan.paymentservices.document.PaymentTransferDocument;
import com.elite4.anandan.paymentservices.dto.TransferRequest;
import com.elite4.anandan.paymentservices.dto.TransferResponse;
import com.elite4.anandan.paymentservices.repository.LinkedAccountRepository;
import com.elite4.anandan.paymentservices.repository.PaymentTransferRepository;
import com.razorpay.RazorpayClient;
import com.razorpay.Transfer;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class RouteService {

    private final RazorpayClient razorpayClient;
    private final PaymentTransferRepository transferRepository;
    private final LinkedAccountRepository linkedAccountRepository;
    private final int platformFee;
    private final boolean routeEnabled;

    public RouteService(@Value("${razorpay.keyId}") String keyId,
                        @Value("${razorpay.keySecret}") String keySecret,
                        @Value("${razorpay.platformFee:4900}") int platformFee,
                        @Value("${razorpay.routeEnabled:true}") boolean routeEnabled,
                        PaymentTransferRepository transferRepository,
                        LinkedAccountRepository linkedAccountRepository) throws Exception {
        this.razorpayClient = new RazorpayClient(keyId, keySecret);
        this.transferRepository = transferRepository;
        this.linkedAccountRepository = linkedAccountRepository;
        this.platformFee = platformFee;
        this.routeEnabled = routeEnabled;
        log.info("RouteService initialized: platformFee={}paise (₹{}), routeEnabled={}",
                platformFee, platformFee / 100.0, routeEnabled);
    }

    public TransferResponse initiateTransfer(String razorpayPaymentId, String razorpayOrderId,
                                              int totalAmount, String ownerUsername, String coliveName,
                                              String registrationId, String tenantName) {
        log.info("Initiating transfer: paymentId={}, amount={}, owner={}, colive={}",
                razorpayPaymentId, totalAmount, ownerUsername, coliveName);

        if (!routeEnabled) {
            log.info("Route is disabled. Skipping transfer for paymentId={}", razorpayPaymentId);
            return TransferResponse.builder()
                    .paymentId(razorpayPaymentId)
                    .amount(totalAmount)
                    .platformFee(0)
                    .ownerAmount(totalAmount)
                    .status("SKIPPED")
                    .message("Route transfers are disabled.")
                    .build();
        }

        // Check for duplicate transfer
        Optional<PaymentTransferDocument> existingTransfer = transferRepository.findByRazorpayPaymentId(razorpayPaymentId);
        if (existingTransfer.isPresent()) {
            PaymentTransferDocument existing = existingTransfer.get();
            log.info("Transfer already exists for paymentId={}: transferId={}, status={}",
                    razorpayPaymentId, existing.getId(), existing.getStatus());
            return TransferResponse.builder()
                    .transferId(existing.getId())
                    .razorpayTransferId(existing.getRazorpayTransferId())
                    .paymentId(razorpayPaymentId)
                    .amount(existing.getTotalAmount())
                    .platformFee(existing.getPlatformFee())
                    .ownerAmount(existing.getOwnerAmount())
                    .status(existing.getStatus())
                    .message("Transfer already processed.")
                    .build();
        }

        // Find PRIMARY linked account for the owner
        Optional<LinkedAccountDocument> linkedAccount = linkedAccountRepository
                .findByOwnerUsernameAndColiveNameAndPrimaryTrue(ownerUsername, coliveName);

        if (linkedAccount.isEmpty()) {
            log.warn("No primary linked account found for owner={}, colive={}. Cannot transfer.", ownerUsername, coliveName);
            // Save as FAILED transfer record
            PaymentTransferDocument failedDoc = createTransferDocument(
                    razorpayPaymentId, razorpayOrderId, null, null,
                    ownerUsername, coliveName, registrationId, tenantName,
                    totalAmount, "FAILED", "No primary linked account found for owner.");
            transferRepository.save(failedDoc);

            return TransferResponse.builder()
                    .paymentId(razorpayPaymentId)
                    .amount(totalAmount)
                    .platformFee(platformFee)
                    .ownerAmount(totalAmount - platformFee)
                    .status("FAILED")
                    .message("No primary bank account configured for owner " + ownerUsername + " at " + coliveName)
                    .build();
        }

        LinkedAccountDocument account = linkedAccount.get();
        int ownerAmount = totalAmount - platformFee;

        if (ownerAmount <= 0) {
            log.error("Owner amount is non-positive: totalAmount={}, platformFee={}, ownerAmount={}",
                    totalAmount, platformFee, ownerAmount);
            return TransferResponse.builder()
                    .paymentId(razorpayPaymentId)
                    .amount(totalAmount)
                    .platformFee(platformFee)
                    .ownerAmount(ownerAmount)
                    .status("FAILED")
                    .message("Payment amount too small to cover platform fee.")
                    .build();
        }

        try {
            // Create transfer via Razorpay Route API
            JSONObject transferRequest = new JSONObject();
            transferRequest.put("account", account.getRazorpayAccountId());
            transferRequest.put("amount", ownerAmount);
            transferRequest.put("currency", "INR");

            JSONObject notes = new JSONObject();
            notes.put("registrationId", registrationId);
            notes.put("tenantName", tenantName);
            notes.put("coliveName", coliveName);
            notes.put("ownerUsername", ownerUsername);
            notes.put("platformFee", platformFee);
            transferRequest.put("notes", notes);

            log.info("Razorpay API → POST /v1/payments/{}/transfers, payload={}",
                    razorpayPaymentId, transferRequest);

            // Execute transfer via Razorpay SDK
            List<Transfer> transfers = razorpayClient.payments.transfer(razorpayPaymentId, transferRequest);
            Transfer transfer = transfers.get(0);
            String razorpayTransferId = transfer.get("id").toString();

            log.info("Razorpay transfer created: transferId={}, amount={}, status={}",
                    razorpayTransferId, transfer.get("amount"), transfer.get("status"));

            // Save transfer document
            PaymentTransferDocument doc = createTransferDocument(
                    razorpayPaymentId, razorpayOrderId, account.getId(), razorpayTransferId,
                    ownerUsername, coliveName, registrationId, tenantName,
                    totalAmount, "PROCESSED", null);
            doc.setProcessedAt(LocalDateTime.now());
            PaymentTransferDocument saved = transferRepository.save(doc);

            return TransferResponse.builder()
                    .transferId(saved.getId())
                    .razorpayTransferId(razorpayTransferId)
                    .paymentId(razorpayPaymentId)
                    .amount(totalAmount)
                    .platformFee(platformFee)
                    .ownerAmount(ownerAmount)
                    .status("PROCESSED")
                    .message("Transfer initiated successfully. ₹" + (ownerAmount / 100.0) + " to owner, ₹" + (platformFee / 100.0) + " platform fee.")
                    .build();

        } catch (Exception e) {
            log.error("Failed to create Razorpay transfer for paymentId={}: {}", razorpayPaymentId, e.getMessage(), e);

            // Save failed transfer document
            PaymentTransferDocument failedDoc = createTransferDocument(
                    razorpayPaymentId, razorpayOrderId, account.getId(), null,
                    ownerUsername, coliveName, registrationId, tenantName,
                    totalAmount, "FAILED", e.getMessage());
            transferRepository.save(failedDoc);

            return TransferResponse.builder()
                    .paymentId(razorpayPaymentId)
                    .amount(totalAmount)
                    .platformFee(platformFee)
                    .ownerAmount(ownerAmount)
                    .status("FAILED")
                    .message("Transfer failed: " + e.getMessage())
                    .build();
        }
    }

    public void markTransferSettled(String razorpayTransferId) {
        transferRepository.findByRazorpayTransferId(razorpayTransferId).ifPresent(doc -> {
            doc.setStatus("SETTLED");
            doc.setSettledAt(LocalDateTime.now());
            transferRepository.save(doc);
            log.info("Transfer marked as SETTLED: razorpayTransferId={}", razorpayTransferId);
        });
    }

    public List<PaymentTransferDocument> getTransfersByOwner(String ownerUsername) {
        return transferRepository.findByOwnerUsername(ownerUsername);
    }

    public Optional<PaymentTransferDocument> getTransferByPaymentId(String razorpayPaymentId) {
        return transferRepository.findByRazorpayPaymentId(razorpayPaymentId);
    }

    public int getPlatformFee() {
        return platformFee;
    }

    public boolean isRouteEnabled() {
        return routeEnabled;
    }

    private PaymentTransferDocument createTransferDocument(String razorpayPaymentId, String razorpayOrderId,
                                                            String linkedAccountId, String razorpayTransferId,
                                                            String ownerUsername, String coliveName,
                                                            String registrationId, String tenantName,
                                                            int totalAmount, String status, String failureReason) {
        PaymentTransferDocument doc = new PaymentTransferDocument();
        doc.setRazorpayPaymentId(razorpayPaymentId);
        doc.setRazorpayOrderId(razorpayOrderId);
        doc.setRazorpayTransferId(razorpayTransferId);
        doc.setLinkedAccountId(linkedAccountId);
        doc.setOwnerUsername(ownerUsername);
        doc.setColiveName(coliveName);
        doc.setRegistrationId(registrationId);
        doc.setTenantName(tenantName);
        doc.setTotalAmount(totalAmount);
        doc.setPlatformFee(platformFee);
        doc.setOwnerAmount(totalAmount - platformFee);
        doc.setStatus(status);
        doc.setFailureReason(failureReason);
        doc.setCreatedAt(LocalDateTime.now());
        return doc;
    }
}
