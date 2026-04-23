package com.elite4.anandan.registrationservices.dto;

import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    private String email;
    private String phoneNumber;
}
