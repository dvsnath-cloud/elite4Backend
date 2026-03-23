package com.elite4.anandan.registrationservices.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.HashSet;
import java.util.Set;

/**
 * Request DTO for user creation (signup).
 */
@Data
public class UserCreateRequest {

    /* ============================
       Fields with Validations
       ============================ */

    @NotBlank(message = "Username is required for login")
    @Size(min = 3, max = 50)
    private String username;

    @NotBlank(message = "ownerOfClient is required")
    @Size(min = 3, max = 50)
    private String ownerOfClient;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Size(min = 10, max = 15, message = "Phone number must be between 10 and 15 characters")
    private String phoneNumber;

    private Set<String> roleIds = new HashSet<>();

    @NotEmpty(message = "At least one clientName is required")
    @Size(max = 50, message = "No more than 50 client names are allowed")
    private Set<ClientNameAndRooms> clientDetails;

    private boolean active;

}