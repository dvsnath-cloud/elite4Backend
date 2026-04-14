package com.elite4.anandan.notificationservices.dto;

import lombok.Data;
import java.util.Map;

@Data
public class NotificationRequest {
    private String email;
    private String phoneNumber;
    private String chatId;          // Telegram chat ID
    private String subject;
    private String message;
    private String templateName;    // e.g., "registration-success", "payment-success"
    private Map<String, Object> variables; // for template variables
}
