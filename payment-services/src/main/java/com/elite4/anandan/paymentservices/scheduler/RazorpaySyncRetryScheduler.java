package com.elite4.anandan.paymentservices.scheduler;

import com.elite4.anandan.paymentservices.service.LinkedAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that retries Razorpay account creation for linked accounts
 * that failed to sync during registration (razorpaySynced=false).
 *
 * Runs every 30 minutes. Can also be triggered manually via
 * POST /payments/route/linked-accounts/retry-sync
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpaySyncRetryScheduler {

    private final LinkedAccountService linkedAccountService;

    @Scheduled(fixedDelayString = "${razorpay.sync.retry.interval:1800000}") // default 30 minutes
    public void retryUnsynced() {
        log.info("Scheduled Razorpay sync retry starting...");
        try {
            int synced = linkedAccountService.retryUnsyncedAccounts();
            if (synced > 0) {
                log.info("Scheduled sync retry completed: {} accounts synced.", synced);
            }
        } catch (Exception e) {
            log.error("Scheduled sync retry failed: {}", e.getMessage(), e);
        }
    }
}
