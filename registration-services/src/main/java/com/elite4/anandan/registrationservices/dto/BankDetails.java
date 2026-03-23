package com.elite4.anandan.registrationservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Bank Details containing account information and UPI ID.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankDetails {

    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "^[0-9]{9,18}$", message = "Account number must be between 9 and 18 digits")
    private String accountNumber;

    @NotBlank(message = "Bank name is required")
    private String bankName;

    @NotBlank(message = "Branch code is required")
    @Pattern(regexp = "^[A-Z0-9]{4,}$", message = "Branch code must contain alphanumeric characters")
    private String branchCode;

    @NotBlank(message = "IFSC code is required")
    @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "IFSC code must be in valid format (e.g., HDFC0000001)")
    private String ifscCode;

    @NotBlank(message = "UPI ID is required")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+@[a-zA-Z0-9]+$", message = "UPI ID must be in valid format (e.g., john.doe@hdfc)")
    private String upiId;
}

