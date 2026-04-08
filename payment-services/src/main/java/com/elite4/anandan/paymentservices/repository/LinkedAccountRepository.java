package com.elite4.anandan.paymentservices.repository;

import com.elite4.anandan.paymentservices.document.LinkedAccountDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LinkedAccountRepository extends MongoRepository<LinkedAccountDocument, String> {
    List<LinkedAccountDocument> findByOwnerUsernameAndColiveName(String ownerUsername, String coliveName);
    Optional<LinkedAccountDocument> findByOwnerUsernameAndColiveNameAndPrimaryTrue(String ownerUsername, String coliveName);
    Optional<LinkedAccountDocument> findByRazorpayAccountId(String razorpayAccountId);
    List<LinkedAccountDocument> findByOwnerUsername(String ownerUsername);
}
