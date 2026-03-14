package com.elite4.anandan.notificationservices.controller;

import com.elite4.anandan.notificationservices.dto.NotificationRequest;
import com.elite4.anandan.notificationservices.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/email")
    public ResponseEntity<Void> sendEmail(@RequestBody NotificationRequest request) {
        notificationService.sendEmail(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/sms")
    public ResponseEntity<Void> sendSms(@RequestBody NotificationRequest request) {
        notificationService.sendSms(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
