package com.elite4.anandan.paymentservices.service;

import com.elite4.anandan.paymentservices.document.PaymentRefundDocument;
import com.elite4.anandan.paymentservices.document.PaymentTransferDocument;
import com.elite4.anandan.paymentservices.dto.RefundRequest;
import com.elite4.anandan.paymentservices.dto.RefundResponse;
import com.elite4.anandan.paymentservices.repository.PaymentRefundRepository;
import com.elite4.anandan.paymentservices.repository.PaymentTransferRepository;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.Refund;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class RefundService {

    private final RazorpayClient razorpayClient;
    private final PaymentRefundRepository refundRepository;
    private final PaymentTransferRepository transferRepository;
    private final NotificationClient notificationClient;

    public RefundService(@Value("${razorpay.keyId}") String keyId,
                         @Value("${razorpay.keySecret}") String keySecret,
                         PaymentRefundRepository refundRepository,
                         PaymentTransferRepository transferRepository,
                         NotificationClient notificationClient) throws Exception {
        this.razorpayClient = new RazorpayClient(keyId, keySecret);
        this.refundRepository = refundRepository;
        this.transferRepository = transferRepository;
        this.notificationClient = notificationClient;
    }

    public RefundResponse initiateRefund(RefundRequest request) {
        String paymentId = request.getRazorpayPaymentId();
        log.info("Initiating refund for paymentId={}, amount={}, reverseTransfer={}",
                paymentId, request.getAmount(), request.isReverseTransfer());

        try {
            // Fetch original transfer info
            Optional<PaymentTransferDocument> transferOpt = transferRepository.findByRazorpayPaymentId(paymentId);
            PaymentTransferDocument transfer = transferOpt.orElse(null);

            // Prepare Razorpay refund request
            JSONObject refundReq = new JSONObject();
            if (request.getAmount() > 0) {
                refundReq.put("amount", request.getAmount());
            }
            if (request.getReason() != null && !request.getReason().isBlank()) {
                JSONObject notes = new JSONObject();
                notes.put("reason", request.getReason());
                refundReq.put("notes", notes);
            }
            if (request.isReverseTransfer()) {
                refundReq.put("reverse_all", 1);
            }

            log.info("Razorpay API → POST /v1/payments/{}/refund, payload={}", paymentId, refundReq);

            // Execute refund
            Payment payment = razorpayClient.payments.fetch(paymentId);
            Refund refund = payment.refund(refundReq);

            String razorpayRefundId = refund.get("id").toString();
            int refundAmount = refund.has("amount") ? Integer.parseInt(refund.get("amount").toString()) : request.getAmount();

            log.info("Razorpay refund created: refundId={}, amount={}", razorpayRefundId, refundAmount);

            // Save refund document
            PaymentRefundDocument doc = new PaymentRefundDocument();
            doc.setRazorpayPaymentId(paymentId);
            doc.setRazorpayRefundId(razorpayRefundId);
            doc.setRazorpayOrderId(transfer != null ? transfer.getRazorpayOrderId() : null);
            doc.setOwnerUsername(transfer != null ? transfer.getOwnerUsername() : null);
            doc.setColiveName(transfer != null ? transfer.getColiveName() : null);
            doc.setTenantName(transfer != null ? transfer.getTenantName() : null);
            doc.setRegistrationId(transfer != null ? transfer.getRegistrationId() : null);
            doc.setOriginalAmount(transfer != null ? transfer.getTotalAmount() : 0);
            doc.setRefundAmount(refundAmount);
            doc.setReason(request.getReason());
            doc.setTransferReversed(request.isReverseTransfer());
            doc.setStatus("PROCESSED");
            doc.setCreatedAt(LocalDateTime.now());
            doc.setProcessedAt(LocalDateTime.now());
            PaymentRefundDocument saved = refundRepository.save(doc);

            // Update transfer status if exists
            if (transfer != null && request.isReverseTransfer()) {
                transfer.setStatus("REVERSED");
                transferRepository.save(transfer);
                log.info("Transfer marked as REVERSED for paymentId={}", paymentId);
            }

            // Send notification
            if (transfer != null && transfer.getTenantName() != null) {
                try {
                    notificationClient.sendEmail(
                            null, // We don't have tenant email in transfer doc
                            "Refund Processed - CoLive Connect",
                            String.format("Dear %s, your refund of ₹%.2f for payment %s has been processed. " +
                                            "Refund ID: %s. Amount will be credited within 5-7 business days.",
                                    transfer.getTenantName(), refundAmount / 100.0,
                                    paymentId, razorpayRefundId));
                } catch (Exception notifEx) {
                    log.warn("Failed to send refund notification: {}", notifEx.getMessage());
                }
            }

            return RefundResponse.builder()
                    .refundId(saved.getId())
                    .razorpayRefundId(razorpayRefundId)
                    .razorpayPaymentId(paymentId)
                    .amount(refundAmount)
                    .status("PROCESSED")
                    .transferReversed(request.isReverseTransfer())
                    .message("Refund of ₹" + (refundAmount / 100.0) + " processed successfully.")
                    .build();

        } catch (Exception e) {
            log.error("Refund failed for paymentId={}: {}", paymentId, e.getMessage(), e);

            // Save failed refund
            PaymentRefundDocument failedDoc = new PaymentRefundDocument();
            failedDoc.setRazorpayPaymentId(paymentId);
            failedDoc.setRefundAmount(request.getAmount());
            failedDoc.setReason(request.getReason());
            failedDoc.setStatus("FAILED");
            failedDoc.setFailureReason(e.getMessage());
            failedDoc.setCreatedAt(LocalDateTime.now());
            refundRepository.save(failedDoc);

            return RefundResponse.builder()
                    .razorpayPaymentId(paymentId)
                    .amount(request.getAmount())
                    .status("FAILED")
                    .transferReversed(false)
                    .message("Refund failed: " + e.getMessage())
                    .build();
        }
    }

    public List<PaymentRefundDocument> getRefundsByPaymentId(String paymentId) {
        return refundRepository.findByRazorpayPaymentId(paymentId);
    }

    public List<PaymentRefundDocument> getRefundsByOwner(String ownerUsername) {
        return refundRepository.findByOwnerUsername(ownerUsername);
    }
}
