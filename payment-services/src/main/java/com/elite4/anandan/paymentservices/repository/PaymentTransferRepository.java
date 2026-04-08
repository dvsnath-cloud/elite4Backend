package com.elite4.anandan.paymentservices.repository;

import com.elite4.anandan.paymentservices.document.PaymentTransferDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransferRepository extends MongoRepository<PaymentTransferDocument, String> {
    Optional<PaymentTransferDocument> findByRazorpayPaymentId(String razorpayPaymentId);
    Optional<PaymentTransferDocument> findByRazorpayTransferId(String razorpayTransferId);
    List<PaymentTransferDocument> findByOwnerUsernameAndStatus(String ownerUsername, String status);
    List<PaymentTransferDocument> findByOwnerUsername(String ownerUsername);

    // Phase 3 — reporting queries
    List<PaymentTransferDocument> findByOwnerUsernameAndColiveName(String ownerUsername, String coliveName);
    List<PaymentTransferDocument> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
    List<PaymentTransferDocument> findByOwnerUsernameAndCreatedAtBetween(String ownerUsername, LocalDateTime from, LocalDateTime to);
    List<PaymentTransferDocument> findByOwnerUsernameAndColiveNameAndCreatedAtBetween(
            String ownerUsername, String coliveName, LocalDateTime from, LocalDateTime to);
}
