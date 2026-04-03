package com.elite4.anandan.registrationservices.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TransferRequestDTO {

    @NotBlank(message = "Tenant registration ID is required")
    private String tenantRegistrationId;

    @NotBlank(message = "Destination CoLive username is required")
    private String toColiveUserName;

    @NotBlank(message = "Destination CoLive name is required")
    private String toColiveName;

    // One of these must be provided
    private String toRoomNumber;
    private String toHouseNumber;
}
