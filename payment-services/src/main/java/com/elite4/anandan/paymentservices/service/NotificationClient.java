package com.elite4.anandan.paymentservices.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class NotificationClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NotificationClient(RestTemplate restTemplate,
                              @Value("${notification.service.url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        log.info("NotificationClient initialized with baseUrl={}", baseUrl);
    }

    public void sendEmail(String to, String subject, String message) {
        String url = baseUrl + "/notifications/email";
        Map<String, Object> req = new HashMap<>();
        req.put("email", to);
        req.put("subject", subject);
        req.put("message", message);
        log.info("POST {} → to={}, subject={}", url, to, subject);
        try {
            restTemplate.postForLocation(url, req);
            log.info("POST {} → email sent successfully", url);
        } catch (Exception e) {
            log.error("POST {} → FAILED: {}", url, e.getMessage());
        }
    }

    public void sendEmailWithTemplate(String to, String subject, String templateName, Map<String, Object> variables) {
        String url = baseUrl + "/notifications/email";
        Map<String, Object> req = new HashMap<>();
        req.put("email", to);
        req.put("subject", subject);
        req.put("templateName", templateName);
        req.put("variables", variables);
        log.info("POST {} → to={}, subject={}, template={}", url, to, subject, templateName);
        try {
            restTemplate.postForLocation(url, req);
            log.info("POST {} → template email sent successfully", url);
        } catch (Exception e) {
            log.error("POST {} → FAILED: {}", url, e.getMessage());
        }
    }

    public void sendSms(String phoneNumber, String message) {
        String url = baseUrl + "/notifications/sms";
        Map<String, Object> req = new HashMap<>();
        req.put("phoneNumber", phoneNumber);
        req.put("message", message);
        log.info("POST {} → phoneNumber={}, message={}", url, phoneNumber, message);
        try {
            restTemplate.postForLocation(url, req);
            log.info("POST {} → SMS sent successfully", url);
        } catch (Exception e) {
            log.error("POST {} → FAILED: {}", url, e.getMessage());
        }
    }

    public void sendSmsWithTemplate(String phoneNumber, String templateName, Map<String, Object> variables) {
        String url = baseUrl + "/notifications/sms";
        Map<String, Object> req = new HashMap<>();
        req.put("phoneNumber", phoneNumber);
        req.put("templateName", templateName);
        req.put("variables", variables);
        log.info("POST {} → phoneNumber={}, template={}", url, phoneNumber, templateName);
        try {
            restTemplate.postForLocation(url, req);
            log.info("POST {} → template SMS sent successfully", url);
        } catch (Exception e) {
            log.error("POST {} → FAILED: {}", url, e.getMessage());
        }
    }

    public void sendWhatsapp(String phoneNumber, String message) {
        String url = baseUrl + "/notifications/whatsapp";
        Map<String, Object> req = new HashMap<>();
        req.put("phoneNumber", phoneNumber);
        req.put("message", message);
        log.info("POST {} → phoneNumber={}", url, phoneNumber);
        try {
            restTemplate.postForLocation(url, req);
            log.info("POST {} → WhatsApp sent successfully", url);
        } catch (Exception e) {
            log.error("POST {} → FAILED: {}", url, e.getMessage());
        }
    }

    public void sendTelegram(String chatId, String message) {
        String url = baseUrl + "/notifications/telegram";
        Map<String, Object> req = new HashMap<>();
        req.put("chatId", chatId);
        req.put("message", message);
        log.info("POST {} → chatId={}", url, chatId);
        try {
            restTemplate.postForLocation(url, req);
            log.info("POST {} → Telegram sent successfully", url);
        } catch (Exception e) {
            log.error("POST {} → FAILED: {}", url, e.getMessage());
        }
    }
}
