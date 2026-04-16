package com.elite4.anandan.registrationservices.repository;

import com.elite4.anandan.registrationservices.document.TenantJobDetailDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenantJobDetailRepository extends MongoRepository<TenantJobDetailDocument, String> {

    List<TenantJobDetailDocument> findByJobId(String jobId);

    long countByJobId(String jobId);
}
