package com.elite4.anandan.registrationservices.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
import java.util.Set;

/**
 * Request DTO for adding client to existing user.
 */
@Data
public class AddClientToUser {

    @NotBlank(message = "Username is required")
    private String username;

    private String email;

    private String phoneNumber;

    @NotNull(message = "CategoryType is required")
    private categoryValues categoryType;

    @NotBlank(message = "Client name is required")
    private String coliveName;

    @NotEmpty(message = "At least one room is required")
    private Set<@Valid Room> rooms;

    @Valid
    private List<BankDetails> bankDetailsList;

    // KYC / Business Details (for Razorpay Route onboarding)
    private String panNumber;
    private String gstNumber;
    private String legalBusinessName;
    private String businessType;
    private String businessAddress;

    public enum categoryValues {
        HOUSE, PG, FLAT, HOSTEL
    }

    private String aadharPhotoPath;

    private String documentUploadPath;

    private String documentType;

    private String documentNumber;
}
