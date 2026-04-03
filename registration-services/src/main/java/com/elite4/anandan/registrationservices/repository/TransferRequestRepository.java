package com.elite4.anandan.registrationservices.repository;

import com.elite4.anandan.registrationservices.document.TransferRequestDocument;
import com.elite4.anandan.registrationservices.document.TransferRequestDocument.TransferStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransferRequestRepository extends MongoRepository<TransferRequestDocument, String> {

    List<TransferRequestDocument> findByStatus(TransferStatus status);

    List<TransferRequestDocument> findByFromColiveUserName(String fromColiveUserName);

    List<TransferRequestDocument> findByToColiveUserName(String toColiveUserName);

    List<TransferRequestDocument> findByTenantRegistrationId(String tenantRegistrationId);

    List<TransferRequestDocument> findByFromColiveUserNameOrToColiveUserName(
            String fromColiveUserName, String toColiveUserName);

    List<TransferRequestDocument> findByStatusAndFromColiveUserName(
            TransferStatus status, String fromColiveUserName);

    List<TransferRequestDocument> findByStatusAndToColiveUserName(
            TransferStatus status, String toColiveUserName);

    List<TransferRequestDocument> findByStatusIn(List<TransferStatus> statuses);
}
