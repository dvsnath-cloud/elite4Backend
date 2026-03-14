package com.elite4.anandan.notificationservices.service;

import com.elite4.anandan.notificationservices.dto.NotificationRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${twilio.sid}")
    private String twilioSid;
    @Value("${twilio.token}")
    private String twilioToken;
    @Value("${twilio.from}")
    private String twilioFrom;

    @Async
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public void sendEmail(NotificationRequest request) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(request.getEmail());
            helper.setSubject(request.getSubject() != null ? request.getSubject() : "Notification");

            String content;
            if (request.getTemplateName() != null && !request.getTemplateName().isEmpty()) {
                Context context = new Context();
                if (request.getVariables() != null) {
                    for (Map.Entry<String, Object> entry : request.getVariables().entrySet()) {
                        context.setVariable(entry.getKey(), entry.getValue());
                    }
                }
                content = templateEngine.process(request.getTemplateName(), context);
                helper.setText(content, true); // true for HTML
            } else {
                content = request.getMessage() != null ? request.getMessage() : "";
                helper.setText(content);
            }

            mailSender.send(message);
            log.info("Sent email to {}", request.getEmail());
        } catch (MessagingException e) {
            log.error("Failed to send email to {}", request.getEmail(), e);
            throw new RuntimeException("Email sending failed", e);
        }
    }

    @Async
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public void sendSms(NotificationRequest request) {
        if (twilioSid == null || twilioSid.isEmpty()) {
            log.warn("Twilio credentials not configured, SMS not sent");
            return;
        }
        try {
            com.twilio.Twilio.init(twilioSid, twilioToken);
            String message = request.getMessage() != null ? request.getMessage() : "";
            if (request.getTemplateName() != null) {
                // For SMS, simple variable replacement
                if (request.getVariables() != null) {
                    for (Map.Entry<String, Object> entry : request.getVariables().entrySet()) {
                        message = message.replace("{{" + entry.getKey() + "}}", entry.getValue().toString());
                    }
                }
            }
            com.twilio.rest.api.v2010.account.Message.creator(
                    new com.twilio.type.PhoneNumber(request.getPhoneNumber()),
                    new com.twilio.type.PhoneNumber(twilioFrom),
                    message)
                .create();
            log.info("Sent SMS to {}", request.getPhoneNumber());
        } catch (Exception e) {
            log.error("Failed to send SMS to {}", request.getPhoneNumber(), e);
            throw new RuntimeException("SMS sending failed", e);
        }
    }
}
