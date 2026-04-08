package com.elite4.anandan.paymentservices.repository;

import com.elite4.anandan.paymentservices.document.PaymentRefundDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRefundRepository extends MongoRepository<PaymentRefundDocument, String> {
    List<PaymentRefundDocument> findByRazorpayPaymentId(String razorpayPaymentId);
    Optional<PaymentRefundDocument> findByRazorpayRefundId(String razorpayRefundId);
    List<PaymentRefundDocument> findByOwnerUsername(String ownerUsername);
    List<PaymentRefundDocument> findByOwnerUsernameAndColiveName(String ownerUsername, String coliveName);
}
