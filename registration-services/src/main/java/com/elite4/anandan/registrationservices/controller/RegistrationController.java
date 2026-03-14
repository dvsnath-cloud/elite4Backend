package com.elite4.anandan.registrationservices.controller;

import com.elite4.anandan.registrationservices.dto.Registration;
import com.elite4.anandan.registrationservices.dto.RegistrationWithRoomRequest;
import com.elite4.anandan.registrationservices.dto.UpdateUserForCheckOut;
import com.elite4.anandan.registrationservices.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
@RestController
@RequestMapping("/registrations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping("/onboardUser")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<RegistrationWithRoomRequest> create(@RequestBody RegistrationWithRoomRequest request) {
        RegistrationWithRoomRequest created = registrationService.create(
                request.getRegistration(),
                request.getRoom());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/updateUser/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<RegistrationWithRoomRequest> update(@PathVariable String id,@RequestBody RegistrationWithRoomRequest request,@RequestParam Boolean changingRoom) {
        return registrationService.update(id, request.getRegistration(),request.getRoom(),changingRoom)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/checkout")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<RegistrationWithRoomRequest> updateCheckOutDateById(@RequestBody UpdateUserForCheckOut updateUserForCheckOut) {
        return registrationService.updateCheckOutDateByID(updateUserForCheckOut.getRegistrationId(), updateUserForCheckOut.getCheckOutDate(),updateUserForCheckOut.getOccupied())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER','GUEST')")
    public ResponseEntity<RegistrationWithRoomRequest> findById(@PathVariable String id) {
        return registrationService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/email/{email}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER','GUEST')")
    public ResponseEntity<RegistrationWithRoomRequest> findByEmail(@PathVariable String email) {
        return registrationService.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/contact/{contactNo}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER','GUEST')")
    public ResponseEntity<RegistrationWithRoomRequest> findByContactNo(@PathVariable String contactNo) {
        return registrationService.findByContactNo(contactNo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<RegistrationWithRoomRequest> findAll() {
        return registrationService.findAll();
    }

    @GetMapping("/user/room/{roomNumber}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public List<RegistrationWithRoomRequest> findAllByRoomNumber( @RequestParam String clientUserName,
                                                   @RequestParam String clientName,@PathVariable String roomNumber) {
        return registrationService.findAllByRoomNumber(clientUserName,clientName,roomNumber);
    }

    @GetMapping("/user/house/{houseNumber}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public List<RegistrationWithRoomRequest> findAllByHouseNumber(@RequestParam String clientUserName,
                                                   @RequestParam String clientName,@PathVariable String houseNumber) {
        return registrationService.findAllByHouseNumber(clientUserName, clientName,houseNumber);
    }

    @GetMapping("/user/room/{roomType}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public List<RegistrationWithRoomRequest> findAllByRoomType(@RequestParam String clientUserName,
                                                @RequestParam String clientName,@PathVariable String roomType) {
        return registrationService.findAllByRoomType(clientUserName,clientName,roomType);
    }

    @GetMapping("/user/house/{houseType}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public List<RegistrationWithRoomRequest> findAllByHouseType(@RequestParam String clientUserName,
                                                 @RequestParam String clientName,@PathVariable String houseType) {
        return registrationService.findAllByHouseType(clientUserName,clientName,houseType);
    }

    @GetMapping("/gender/{gender}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public List<RegistrationWithRoomRequest> findAllByGender(@PathVariable Registration.Gender gender) {
        return registrationService.findAllByGender(gender);
    }

    @GetMapping("/user/fname/{fname}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public List<RegistrationWithRoomRequest> findByFname(@PathVariable String fname) {
        return registrationService.findByFname(fname);
    }

    @GetMapping("/user/lname/{lname}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public List<RegistrationWithRoomRequest> findByLname(@PathVariable String lname) {
        return registrationService.findByLname(lname);
    }

    @GetMapping("/user/mname/{mname}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public List<RegistrationWithRoomRequest> findByMname(@PathVariable String mname) {
        return registrationService.findByMname(mname);
    }

    @GetMapping("/pincode/{pincode}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public List<RegistrationWithRoomRequest> findByPincode(@PathVariable String pincode) {
        return registrationService.findByPincode(pincode);
    }

    @GetMapping("/check-in/{checkInDate}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public List<RegistrationWithRoomRequest> findByCheckInDate(@PathVariable String checkInDate) {
        return registrationService.findByCheckInDate(checkInDate);
    }

    @GetMapping("/check-out/{checkOutDate}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public List<RegistrationWithRoomRequest> findByCheckOutDate(@PathVariable String checkOutDate) {
        return registrationService.findByCheckOutDate(checkOutDate);
    }

    @GetMapping("/client/not-occupied")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public List<RegistrationWithRoomRequest> findRoomsWithNotOccupiedStatus() {
        return registrationService.findRoomsWithNotOccupiedStatus();
    }

    @GetMapping("/client/occupied")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public List<RegistrationWithRoomRequest> findRoomsWithOccupiedStatus() {
        return registrationService.findRoomsWithOccupiedStatus();
    }

    @GetMapping("/exists/email/{email}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<Boolean> existsByEmail(@PathVariable String email) {
        return ResponseEntity.ok(registrationService.existsByEmail(email));
    }

    @GetMapping("/exists/contact/{contactNo}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<Boolean> existsByContactNo(@PathVariable String contactNo) {
        return ResponseEntity.ok(registrationService.existsByContactNo(contactNo));
    }

    @GetMapping("/exists/id/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<Boolean> existsById(@PathVariable String id) {
        return ResponseEntity.ok(registrationService.existsById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<Void> deleteById(@PathVariable String id) {
        if (!registrationService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        registrationService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    
}
