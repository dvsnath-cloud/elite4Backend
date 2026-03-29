package com.elite4.anandan.registrationservices.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Set;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ColiveNameAndRooms {
    @NotBlank(message = "CoLive name is required")
    private String coliveName;

    @NotNull(message = "CategoryType is required")
    private categoryValues categoryType;
    /* ============================
       Bank Details
       ============================ */
    @Valid
    private BankDetails bankDetails;

    @NotEmpty(message = "At least one ROOM OR HOUSE details are required")
    @Size(max = 500, message = "No more than 500 client names are allowed")
    private Set<Room> rooms;

    private String aadharPhotoPath;
    private String documentUploadPath;
    private String documentType;
    private String documentNumber;

    public enum categoryValues {
        HOUSE, PG, FLAT, HOSTEL
    }
}
