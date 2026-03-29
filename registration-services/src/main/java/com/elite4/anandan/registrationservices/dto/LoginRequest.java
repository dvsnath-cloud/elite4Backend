package com.elite4.anandan.registrationservices.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for login.
 * NOW supports authentication by: email OR phoneNumber (not username)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {

    /**
     * Email for login - use this if logging in with email
     */
    private String email;

    /**
     * Phone number for login - use this if logging in with phone number
     * Either email or phoneNumber must be provided (not both required)
     */
    private String phoneNumber;

    /**
     * Password - required for both email and phone login
     */
    @NotBlank(message = "Password is required")
    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}

