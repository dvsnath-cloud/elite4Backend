package com.elite4.anandan.registrationservices.repository;

import java.util.Map;
import java.util.Set;

/**
 * Custom repository for batch aggregation queries on RentPaymentTransaction.
 */
public interface RentPaymentTransactionCustomRepository {

    /**
     * Batch-compute pending balance for a set of tenant IDs in one aggregation query.
     * Returns Map of tenantId -> total remaining amount across all PENDING/PARTIAL/OVERDUE records.
     */
    Map<String, Double> batchCalculatePendingBalances(Set<String> tenantIds);
}
