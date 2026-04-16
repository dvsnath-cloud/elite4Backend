package com.elite4.anandan.registrationservices.repository;

import com.elite4.anandan.registrationservices.document.RegistrationDocument;
import com.elite4.anandan.registrationservices.dto.Registration;
import com.elite4.anandan.registrationservices.dto.RegistrationWithRoomRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistrationRepository extends MongoRepository<RegistrationDocument, String> {
    Optional<RegistrationDocument> findByEmail(String email);
    Optional<RegistrationDocument> findByEmailAndOccupied(String email, Registration.roomOccupied occupied);

    Optional<RegistrationDocument> findByContactNo(String contactNo);
    List<RegistrationDocument> findAllByContactNo(String contactNo);
    Optional<RegistrationDocument> findByContactNoAndOccupied(String contactNo, Registration.roomOccupied occupied);
    List<RegistrationDocument> findByColiveNameAndColiveUserName(String coliveName, String coliveUserName);
    List<RegistrationDocument> findAllByColiveUserNameAndColiveNameAndRoomForRegistrationRoomType(String coliveUserName,String coliveName,String roomType);
    List<RegistrationDocument> findAllByColiveUserNameAndColiveNameAndRoomForRegistrationHouseType(String coliveUserName,String coliveName,String houseType);
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
    List<RegistrationDocument> findByOccupiedAndColiveUserNameAndColiveName(Registration.roomOccupied occupied, String coliveUserName, String coliveName);
    List<RegistrationDocument> findByOccupiedAndColiveUserName(Registration.roomOccupied occupied, String coliveUserName);
    List<RegistrationDocument> findByFnameAndColiveNameAndContactNo(String fname, String coliveName, String contactNo);
    List<RegistrationDocument> findByColiveNameAndColiveUserNameAndRoomForRegistrationRoomNumber( String coliveName, String coliveUserName,String roomNumber);
    List<RegistrationDocument> findByColiveUserNameAndColiveNameAndRoomForRegistrationHouseNumber(String fname, String coliveName, String houseNumber);

    // Query to get registrations excluding VACATED status
    List<RegistrationDocument> findByColiveNameAndColiveUserNameAndRoomForRegistrationRoomNumberAndOccupied(
            String coliveName,
            String coliveUserName,
            String roomNumber,
            Registration.roomOccupied occupied);

    // Query to get registrations excluding VACATED status by house number
    List<RegistrationDocument> findByColiveUserNameAndColiveNameAndRoomForRegistrationHouseNumberAndOccupied(
            String coliveUserName,
            String coliveName,
            String houseNumber,
            Registration.roomOccupied occupied);

    // Query to get all registrations for a house (for house availability calculation)
    List<RegistrationDocument> findByColiveNameAndColiveUserNameAndRoomForRegistrationHouseNumber(
            String coliveName,
            String coliveUserName,
            String houseNumber);

    // Paginated query for occupied tenants (for scheduler batch processing)
    Page<RegistrationDocument> findByOccupied(Registration.roomOccupied occupied, Pageable pageable);

    // Count active tenants (for progress logging)
    long countByOccupied(Registration.roomOccupied occupied);
}
