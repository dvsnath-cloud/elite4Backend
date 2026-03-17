package com.elite4.anandan.registrationservices.repository;

import com.elite4.anandan.registrationservices.document.RegistrationDocument;
import com.elite4.anandan.registrationservices.dto.Registration;
import com.elite4.anandan.registrationservices.dto.RegistrationWithRoomRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistrationRepository extends MongoRepository<RegistrationDocument, String> {
    Optional<RegistrationDocument> findByEmail(String email);
    Optional<RegistrationDocument> findByContactNo(String contactNo);
    List<RegistrationDocument> findByClientNameAndClientUserName(String clientName, String clientUserName);
    List<RegistrationDocument> findAllByClientUserNameAndClientNameAndRoomRoomType(String clientUserName,String clientName,String roomType);
    List<RegistrationDocument> findAllByClientUserNameAndClientNameAndRoomHouseType(String clientUserName,String clientName,String houseType);
    List<RegistrationDocument> findAllByGender(Registration.Gender gender);
    List<RegistrationDocument> findByfname(String fname);
    List<RegistrationDocument> findBylname(String lname);
    List<RegistrationDocument> findBymname(String mname);
    List<RegistrationDocument> findByaddress(String address);
    List<RegistrationDocument> findBypincode(String pincode);
    List<RegistrationDocument> findBydocumentNumber(String documentNumber);
    List<RegistrationDocument> findBydocumentType(Registration.DocumentType documentType);
    List<RegistrationDocument> findBycheckInDate(String checkInDate);   
    List<RegistrationDocument> findBycheckOutDate(String checkOutDate);
    List<RegistrationDocument> findByOccupied(Registration.roomOccupied occupied);
    List<RegistrationDocument> findByFnameAndClientNameAndContactNo(String fname, String clientName, String contactNo);
    List<RegistrationDocument> findByClientUserNameAndClientNameAndRoomRoomNumber(String fname, String clientName, String roomNumber);
    List<RegistrationDocument> findByClientUserNameAndClientNameAndRoomHouseNumber(String fname, String clientName, String houseNumber);
}
