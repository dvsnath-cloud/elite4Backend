package com.elite4.anandan.notificationservices.service;

import com.elite4.anandan.notificationservices.dto.NotificationRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.List;
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

    @Value("${whatsapp.api.url:https://graph.facebook.com/v19.0}")
    private String whatsappApiUrl;
    @Value("${whatsapp.api.token:}")
    private String whatsappApiToken;
    @Value("${whatsapp.phone.number.id:}")
    private String whatsappPhoneNumberId;

    @Value("${telegram.bot.token:}")
    private String telegramBotToken;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Send WhatsApp message via Meta Cloud API (WhatsApp Business Platform).
     * Uses the Graph API to send text messages.
     * Requires: whatsapp.api.token (permanent access token) and whatsapp.phone.number.id
     */
    @Async
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public void sendWhatsapp(NotificationRequest request) {
        if (whatsappApiToken == null || whatsappApiToken.isEmpty()
                || whatsappPhoneNumberId == null || whatsappPhoneNumberId.isEmpty()) {
            log.warn("WhatsApp credentials not configured, message not sent to {}", request.getPhoneNumber());
            return;
        }
        try {
            String url = whatsappApiUrl + "/" + whatsappPhoneNumberId + "/messages";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(whatsappApiToken);

            String messageText = resolveMessage(request);

            Map<String, Object> body = new HashMap<>();
            body.put("messaging_product", "whatsapp");
            body.put("to", sanitizePhoneNumber(request.getPhoneNumber()));
            body.put("type", "text");
            body.put("text", Map.of("body", messageText));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Sent WhatsApp message to {}", request.getPhoneNumber());
            } else {
                log.error("WhatsApp API returned status {}: {}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("WhatsApp API error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to {}", request.getPhoneNumber(), e);
            throw new RuntimeException("WhatsApp sending failed", e);
        }
    }

    /**
     * Send Telegram message via Telegram Bot API.
     * Uses the sendMessage endpoint: https://api.telegram.org/bot{token}/sendMessage
     * Chat ID can come from the request or fall back to the configured default.
     */
    @Async
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public void sendTelegram(NotificationRequest request) {
        if (telegramBotToken == null || telegramBotToken.isEmpty()) {
            log.warn("Telegram bot token not configured, message not sent");
            return;
        }
        String chatId = (request.getChatId() != null && !request.getChatId().isEmpty())
                ? request.getChatId()
                : null;
        if (chatId == null) {
            log.warn("No Telegram chatId provided in request, message not sent");
            return;
        }
        try {
            String url = "https://api.telegram.org/bot" + telegramBotToken + "/sendMessage";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String messageText = resolveMessage(request);

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", messageText);
            body.put("parse_mode", "HTML");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Sent Telegram message to chat {}", chatId);
            } else {
                log.error("Telegram API returned status {}: {}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("Telegram API error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to send Telegram message to chat {}", chatId, e);
            throw new RuntimeException("Telegram sending failed", e);
        }
    }

    /**
     * Resolve message text with template variable substitution for non-email channels.
     */
    private String resolveMessage(NotificationRequest request) {
        String message = request.getMessage() != null ? request.getMessage() : "";
        if (request.getVariables() != null) {
            for (Map.Entry<String, Object> entry : request.getVariables().entrySet()) {
                message = message.replace("{{" + entry.getKey() + "}}", entry.getValue().toString());
            }
        }
        return message;
    }

    /**
     * Sanitize phone number: remove spaces, dashes; ensure it starts with country code.
     */
    private String sanitizePhoneNumber(String phone) {
        if (phone == null) return "";
        String sanitized = phone.replaceAll("[\\s\\-()]", "");
        // Remove leading + for WhatsApp API (expects digits only, e.g., 919876543210)
        if (sanitized.startsWith("+")) {
            sanitized = sanitized.substring(1);
        }
        return sanitized;
    }
}
