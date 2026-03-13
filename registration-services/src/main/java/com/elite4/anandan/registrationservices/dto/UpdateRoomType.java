package com.elite4.anandan.registrationservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request DTO for adding client to existing user.
 */
@Data
public class UpdateRoomType {

    @NotBlank(message = "Username is required")
    private String username;

    private String email;

    private String phoneNumber;

    @NotBlank(message = "Client name is required")
    private String clientName;

    @NotBlank(message = "Room number is required")
    private String roomNumber;

    @NotNull(message = "Room type is required")
    private RoomType roomType;
}
