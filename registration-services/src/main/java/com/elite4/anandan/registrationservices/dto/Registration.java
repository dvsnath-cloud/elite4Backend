package com.elite4.anandan.registrationservices.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Date;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Registration {
    private String regId;

    @NotBlank(message = "fname is required for login")
    @Size(min = 3, max = 50)
    private String fname;

    @NotBlank(message = "lname is required for login")
    @Size(min = 3, max = 50)
    private String lname;

    @Email(message = "Invalid email format")
    private String email;

    private String mname;

    @NotBlank(message = "Phone number is required")
    @Size(min = 10, max = 15, message = "Phone number must be between 10 and 15 characters")
    private String contactNo;

    private Gender gender;

    @NotBlank(message = "address is required")
    private String address;

    private String pincode;

    private String aadharPhotoPath;

    private String documentUploadPath;

    private DocumentType documentType;

    private String documentNumber;

    private String coliveUserName;

    private String coliveName;

    private Date checkInDate;

    private Date checkOutDate;

    private double advanceAmount;

    private roomOccupied roomOccupied;

    private String parentName;

    private String parentContactNo;

    private double roomRent;

    private RoomForRegistration room;

    private String telegramChatId;

    private String transferStatus;

    private Boolean entireRoomOccupied;

    public enum roomOccupied {
        OCCUPIED, NOT_OCCUPIED,VACATED
    }

    public enum Gender {
        MALE, FEMALE, OTHERS
    }

    public enum DocumentType {
        AADHAR, PAN, VOTERID, DRIVINGLICENSE
    }
}

