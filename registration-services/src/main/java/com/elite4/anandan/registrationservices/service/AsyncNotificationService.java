package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.document.SchedulerJobLog.StepStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Async wrapper around NotificationClient for parallel notification dispatch.
 * Uses the "notificationExecutor" thread pool (20 threads, queue 5000).
 *
 * Each method returns a CompletableFuture so callers can batch and await results.
 */
@Service
@Slf4j
public class AsyncNotificationService {

    private final NotificationClient notificationClient;

    public AsyncNotificationService(NotificationClient notificationClient) {
        this.notificationClient = notificationClient;
    }

    @Async("notificationExecutor")
    public CompletableFuture<NotificationChannelResult> sendEmailAsync(String tenantId, String to, String subject, String message) {
        if (to == null || to.isBlank()) {
            return CompletableFuture.completedFuture(
                    new NotificationChannelResult(StepStatus.SKIPPED, "No email configured"));
        }
        try {
            notificationClient.sendEmail(to, subject, message);
            return CompletableFuture.completedFuture(
                    new NotificationChannelResult(StepStatus.SUCCESS, null));
        } catch (Exception e) {
            log.warn("Email failed for tenant {}: {}", tenantId, e.getMessage());
            return CompletableFuture.completedFuture(
                    new NotificationChannelResult(StepStatus.FAILED, e.getMessage()));
        }
    }

    @Async("notificationExecutor")
    public CompletableFuture<NotificationChannelResult> sendSmsAsync(String tenantId, String phoneNumber, String message) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return CompletableFuture.completedFuture(
                    new NotificationChannelResult(StepStatus.SKIPPED, "No contact number configured"));
        }
        try {
            notificationClient.sendSms(phoneNumber, message);
            return CompletableFuture.completedFuture(
                    new NotificationChannelResult(StepStatus.SUCCESS, null));
        } catch (Exception e) {
            log.warn("SMS failed for tenant {}: {}", tenantId, e.getMessage());
            return CompletableFuture.completedFuture(
                    new NotificationChannelResult(StepStatus.FAILED, e.getMessage()));
        }
    }

    @Async("notificationExecutor")
    public CompletableFuture<NotificationChannelResult> sendWhatsappAsync(String tenantId, String phoneNumber, String message) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return CompletableFuture.completedFuture(
                    new NotificationChannelResult(StepStatus.SKIPPED, "No contact number configured"));
        }
        try {
            notificationClient.sendWhatsapp(phoneNumber, message);
            return CompletableFuture.completedFuture(
                    new NotificationChannelResult(StepStatus.SUCCESS, null));
        } catch (Exception e) {
            log.warn("WhatsApp failed for tenant {}: {}", tenantId, e.getMessage());
            return CompletableFuture.completedFuture(
                    new NotificationChannelResult(StepStatus.FAILED, e.getMessage()));
        }
    }

    /**
     * Holds the result of a single notification channel attempt.
     */
    public record NotificationChannelResult(StepStatus status, String error) {}
}
