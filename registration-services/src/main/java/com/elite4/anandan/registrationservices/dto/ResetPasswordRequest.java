package com.elite4.anandan.registrationservices.dto;

import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String email;
    private String phoneNumber;
    private String otp;
    private String newPassword;
}
