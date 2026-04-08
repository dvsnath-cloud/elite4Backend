package com.elite4.anandan.paymentservices.service;

import com.elite4.anandan.paymentservices.document.PaymentTransferDocument;
import com.elite4.anandan.paymentservices.dto.PlatformEarningsReport;
import com.elite4.anandan.paymentservices.dto.SettlementReport;
import com.elite4.anandan.paymentservices.repository.PaymentTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementReportService {

    private final PaymentTransferRepository transferRepository;

    /**
     * Owner-level settlement report for a given month.
     */
    public SettlementReport getOwnerSettlementReport(String ownerUsername, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.atEndOfMonth().atTime(23, 59, 59);

        List<PaymentTransferDocument> transfers = transferRepository
                .findByOwnerUsernameAndCreatedAtBetween(ownerUsername, from, to);

        return buildReport(ownerUsername, null, ym.format(DateTimeFormatter.ofPattern("MMMM yyyy")), transfers);
    }

    /**
     * Property-level settlement report for a given month.
     */
    public SettlementReport getColiveSettlementReport(String ownerUsername, String coliveName, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.atEndOfMonth().atTime(23, 59, 59);

        List<PaymentTransferDocument> transfers = transferRepository
                .findByOwnerUsernameAndColiveNameAndCreatedAtBetween(ownerUsername, coliveName, from, to);

        return buildReport(ownerUsername, coliveName, ym.format(DateTimeFormatter.ofPattern("MMMM yyyy")), transfers);
    }

    /**
     * Platform-wide earnings report for admin.
     */
    public PlatformEarningsReport getPlatformEarningsReport(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to = ym.atEndOfMonth().atTime(23, 59, 59);

        List<PaymentTransferDocument> allTransfers = transferRepository.findByCreatedAtBetween(from, to);

        long totalPlatformEarnings = allTransfers.stream().mapToLong(PaymentTransferDocument::getPlatformFee).sum();
        long totalPaymentsProcessed = allTransfers.stream().mapToLong(PaymentTransferDocument::getTotalAmount).sum();

        // Group by owner
        Map<String, List<PaymentTransferDocument>> byOwner = allTransfers.stream()
                .collect(Collectors.groupingBy(PaymentTransferDocument::getOwnerUsername));

        List<PlatformEarningsReport.OwnerSummary> ownerBreakdown = new ArrayList<>();
        byOwner.forEach((owner, ownerTransfers) -> {
            ownerBreakdown.add(PlatformEarningsReport.OwnerSummary.builder()
                    .ownerUsername(owner)
                    .transactionCount(ownerTransfers.size())
                    .totalCollected(ownerTransfers.stream().mapToLong(PaymentTransferDocument::getTotalAmount).sum())
                    .platformFee(ownerTransfers.stream().mapToLong(PaymentTransferDocument::getPlatformFee).sum())
                    .ownerSettled(ownerTransfers.stream().mapToLong(PaymentTransferDocument::getOwnerAmount).sum())
                    .build());
        });

        return PlatformEarningsReport.builder()
                .periodLabel(ym.format(DateTimeFormatter.ofPattern("MMMM yyyy")))
                .totalPlatformEarnings(totalPlatformEarnings)
                .totalPaymentsProcessed(totalPaymentsProcessed)
                .transactionCount(allTransfers.size())
                .ownerBreakdown(ownerBreakdown)
                .build();
    }

    /**
     * Reconciliation — compares transfer statuses.
     */
    public Map<String, Object> getReconciliationSummary(String ownerUsername) {
        List<PaymentTransferDocument> all = transferRepository.findByOwnerUsername(ownerUsername);

        long settled = all.stream().filter(t -> "SETTLED".equals(t.getStatus())).count();
        long processed = all.stream().filter(t -> "PROCESSED".equals(t.getStatus())).count();
        long failed = all.stream().filter(t -> "FAILED".equals(t.getStatus())).count();
        long totalAmount = all.stream().mapToLong(PaymentTransferDocument::getTotalAmount).sum();
        long settledAmount = all.stream()
                .filter(t -> "SETTLED".equals(t.getStatus()))
                .mapToLong(PaymentTransferDocument::getOwnerAmount).sum();

        return Map.of(
                "ownerUsername", ownerUsername,
                "totalTransfers", all.size(),
                "settled", settled,
                "processed", processed,
                "failed", failed,
                "totalAmountPaise", totalAmount,
                "settledAmountPaise", settledAmount,
                "pendingSettlementPaise", totalAmount - settledAmount - all.stream()
                        .filter(t -> "FAILED".equals(t.getStatus()))
                        .mapToLong(PaymentTransferDocument::getTotalAmount).sum()
        );
    }

    private SettlementReport buildReport(String ownerUsername, String coliveName,
                                          String periodLabel, List<PaymentTransferDocument> transfers) {
        long totalCollected = transfers.stream().mapToLong(PaymentTransferDocument::getTotalAmount).sum();
        long totalPlatformFee = transfers.stream().mapToLong(PaymentTransferDocument::getPlatformFee).sum();
        long totalOwnerSettled = transfers.stream().mapToLong(PaymentTransferDocument::getOwnerAmount).sum();
        int settledCount = (int) transfers.stream().filter(t -> "SETTLED".equals(t.getStatus())).count();
        int pendingCount = (int) transfers.stream().filter(t -> "PROCESSED".equals(t.getStatus())).count();
        int failedCount = (int) transfers.stream().filter(t -> "FAILED".equals(t.getStatus())).count();

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        List<SettlementReport.TransferSummaryItem> items = transfers.stream()
                .map(t -> SettlementReport.TransferSummaryItem.builder()
                        .transferId(t.getId())
                        .tenantName(t.getTenantName())
                        .coliveName(t.getColiveName())
                        .totalAmount(t.getTotalAmount())
                        .platformFee(t.getPlatformFee())
                        .ownerAmount(t.getOwnerAmount())
                        .status(t.getStatus())
                        .paymentDate(t.getCreatedAt() != null ? t.getCreatedAt().format(dtf) : null)
                        .settlementDate(t.getSettledAt() != null ? t.getSettledAt().format(dtf) : null)
                        .build())
                .collect(Collectors.toList());

        return SettlementReport.builder()
                .ownerUsername(ownerUsername)
                .coliveName(coliveName)
                .periodLabel(periodLabel)
                .totalPayments(transfers.size())
                .totalAmountCollected(totalCollected)
                .totalPlatformFee(totalPlatformFee)
                .totalOwnerSettled(totalOwnerSettled)
                .settledCount(settledCount)
                .pendingCount(pendingCount)
                .failedCount(failedCount)
                .transfers(items)
                .build();
    }
}
