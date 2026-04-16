package com.elite4.anandan.registrationservices.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Uses MongoTemplate aggregation to batch-compute pending balances
 * for thousands of tenants in a single DB round-trip.
 *
 * Aggregation pipeline:
 *   { $match: { tenantId: { $in: [...] }, status: { $in: ["PENDING","PARTIAL","OVERDUE"] } } }
 *   { $group: { _id: "$tenantId", totalRemaining: { $sum: "$remainingAmount" } } }
 */
@Repository
@RequiredArgsConstructor
public class RentPaymentTransactionCustomRepositoryImpl implements RentPaymentTransactionCustomRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public Map<String, Double> batchCalculatePendingBalances(Set<String> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> statusValues = Arrays.asList("PENDING", "PARTIAL", "OVERDUE");

        MatchOperation match = Aggregation.match(
                Criteria.where("tenantId").in(tenantIds)
                        .and("status").in(statusValues)
        );

        GroupOperation group = Aggregation.group("tenantId")
                .sum("remainingAmount").as("totalRemaining");

        Aggregation aggregation = Aggregation.newAggregation(match, group);

        AggregationResults<PendingBalanceResult> results = mongoTemplate.aggregate(
                aggregation, "rentPaymentTransactions", PendingBalanceResult.class
        );

        Map<String, Double> balanceMap = new HashMap<>();
        for (PendingBalanceResult result : results.getMappedResults()) {
            balanceMap.put(result.getId(), result.getTotalRemaining());
        }
        return balanceMap;
    }

    /**
     * Inner class to hold aggregation result.
     * _id maps to tenantId (from $group), totalRemaining is the sum.
     */
    @lombok.Data
    private static class PendingBalanceResult {
        private String id;          // tenantId (from _id in $group)
        private double totalRemaining;
    }
}
