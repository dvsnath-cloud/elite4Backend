package com.elite4.anandan.registrationservices.dto;

import lombok.Data;

import java.util.Date;

@Data
public class Registration {
    private String regId;
    private String fname;
    private String lname;
    private String email;
    private String mname;
    private String contactNo;
    private Gender gender;
    private String address;
    private String pincode;
    private UploadDocuments uploadDocuments;
    private String photo;
    private DocumentType documentType;
    private String documentNumber;
    private String clientUserName;
    private String clientName;
    private Date checkInDate;
    private Date checkOutDate;
    private double advanceAmount;
    private roomOccupied roomOccupied;
    private double roomRent;
    public enum roomOccupied {
        OCCUPIED, NOT_OCCUPIED,VACATED
    }

    public enum Gender {
        MALE, FEMALE, OTHERS
    }

    public enum UploadDocuments {
        IMAGE_UPLOADS, DOCUMENT_UPLOAD
    }

    public enum DocumentType {
        AADHAR, PAN, VOTERID, DRIVINGLICENSE
    }
}

