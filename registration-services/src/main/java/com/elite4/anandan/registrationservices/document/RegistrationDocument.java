package com.elite4.anandan.registrationservices.document;

import com.elite4.anandan.registrationservices.dto.Registration;
import com.elite4.anandan.registrationservices.dto.RoomForRegistration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "registrations")
@CompoundIndex(name = "unique_email_occupied", def = "{'email': 1, 'occupied': 1}", unique = true, partialFilter = "{'occupied': 'OCCUPIED'}")
@CompoundIndex(name = "unique_contact_occupied", def = "{'contactNo': 1, 'occupied': 1}", unique = true, partialFilter = "{'occupied': 'OCCUPIED'}")
public class RegistrationDocument {

    @Id
    private String id;
    private String fname;
    private String lname;
    private String email;
    private String mname;
    private String contactNo;
    private Registration.Gender gender;
    private String address;
    private String pincode;
    private String aadharPhotoPath;
    private String documentUploadPath;
    private Registration.DocumentType documentType;
    private String documentNumber;
    private Date checkInDate;
    private Date checkOutDate;
    private String coliveName;
    private String coliveUserName;
    private Registration.roomOccupied occupied;
    private RoomForRegistration roomForRegistration;
    private String parentName;
    private String parentContactNo;
    private double advanceAmount;
    private double roomRent;
    private Boolean active;
    private String transferStatus;
    private Boolean entireRoomOccupied;
}
