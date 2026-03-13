package com.elite4.anandan.registrationservices.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class NotificationClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NotificationClient(RestTemplate restTemplate,
                              @Value("${notification.service.url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public void sendEmail(String to, String subject, String message) {
        Map<String, Object> req = new HashMap<>();
        req.put("email", to);
        req.put("subject", subject);
        req.put("message", message);
        restTemplate.postForLocation(baseUrl + "/notifications/email", req);
    }

    public void sendEmailWithTemplate(String to, String subject, String templateName, Map<String, Object> variables) {
        Map<String, Object> req = new HashMap<>();
        req.put("email", to);
        req.put("subject", subject);
        req.put("templateName", templateName);
        req.put("variables", variables);
        restTemplate.postForLocation(baseUrl + "/notifications/email", req);
    }

    public void sendSms(String phoneNumber, String message) {
        Map<String, Object> req = new HashMap<>();
        req.put("phoneNumber", phoneNumber);
        req.put("message", message);
        restTemplate.postForLocation(baseUrl + "/notifications/sms", req);
    }

    public void sendSmsWithTemplate(String phoneNumber, String templateName, Map<String, Object> variables) {
        Map<String, Object> req = new HashMap<>();
        req.put("phoneNumber", phoneNumber);
        req.put("templateName", templateName);
        req.put("variables", variables);
        restTemplate.postForLocation(baseUrl + "/notifications/sms", req);
    }
}
