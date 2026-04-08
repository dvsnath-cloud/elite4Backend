package com.elite4.anandan.paymentservices.repository;

import com.elite4.anandan.paymentservices.document.WebhookEventDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebhookEventRepository extends MongoRepository<WebhookEventDocument, String> {
    Optional<WebhookEventDocument> findByEventId(String eventId);
    boolean existsByEventId(String eventId);
}
