package com.elite4.anandan.registrationservices.document;

import com.elite4.anandan.registrationservices.dto.Registration;
import com.elite4.anandan.registrationservices.dto.Room;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "registrations")
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
    private Registration.UploadDocuments uploadDocuments;
    private String photo;
    private Registration.DocumentType documentType;
    private String documentNumber;
    private Date checkInDate;
    private Date checkOutDate;
    private String clientName;
    private String clientUserName;
    private Registration.roomOccupied occupied;
    private Room room;
    private double advanceAmount;
    private double roomRent;
}

