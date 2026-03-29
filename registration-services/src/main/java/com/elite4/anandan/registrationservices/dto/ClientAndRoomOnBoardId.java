package com.elite4.anandan.registrationservices.dto;

import jakarta.validation.Valid;
import lombok.Data;

@Data
public class ClientAndRoomOnBoardId {
    private String coliveName;
    private String roomOnBoardId;
    private String clientCategory;
    @Valid
    private BankDetails bankDetails;
    private String aadharPhotoPath;
    private String documentUploadPath;
    private String documentType;
    private String documentNumber;
}
