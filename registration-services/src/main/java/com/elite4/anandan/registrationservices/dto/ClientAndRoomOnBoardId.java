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
    private List<BankDetails> bankDetailsList;

    // KYC / Business Details (for Razorpay Route onboarding)
    private String panNumber;
    private String gstNumber;
    private String legalBusinessName;
    private String businessType;
    private String businessAddress;

    private List<String> licenseDocumentsPath;
    private String documentType;
    private String documentNumber;
    private List<String> uploadedPhotos;
    private String aadharPhotoPath;
    private String documentUploadPath;
}
