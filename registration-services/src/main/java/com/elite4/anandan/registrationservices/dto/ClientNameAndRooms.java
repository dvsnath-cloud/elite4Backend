package com.elite4.anandan.registrationservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class ClientNameAndRooms {
    @NotBlank(message = "Client name is required")
    private String clientName;

    @NotNull(message = "CategoryType is required")
    private categoryValues categoryType;

    @NotEmpty(message = "At least one ROOM OR HOUSE details are required")
    @Size(max = 500, message = "No more than 500 client names are allowed")
    private Set<Room> rooms;

    public enum categoryValues {
        HOUSE, PG, FLAT, HOSTEL
    }
}
