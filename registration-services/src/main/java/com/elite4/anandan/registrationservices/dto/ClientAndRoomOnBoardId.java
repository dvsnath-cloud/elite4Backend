package com.elite4.anandan.registrationservices.dto;

import jakarta.validation.Valid;
import lombok.Data;
import java.util.List;

@Data
public class ClientAndRoomOnBoardId {
    private String coliveName;
    private String roomOnBoardId;
    private String clientCategory;
    @Valid
    private BankDetails bankDetails;
    private List<String> licenseDocumentsPath;
    private String documentType;
    private String documentNumber;
    private List<String> uploadedPhotos;
    private String aadharPhotoPath;
    private String documentUploadPath;
}
