package com.elite4.anandan.registrationservices.controller;

import com.elite4.anandan.registrationservices.dto.*;
import com.elite4.anandan.registrationservices.service.RegistrationService;
import com.elite4.anandan.registrationservices.service.RoomAvailabilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.CrossOrigin;
@RestController
@RequestMapping("/registrations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Slf4j
public class RegistrationController {

    private final RegistrationService registrationService;
    private final RoomAvailabilityService roomAvailabilityService;
    private final ObjectMapper objectMapper;

    @PostMapping("/onboardUser")
    @PreAuthorize("hasAnyRole('USER','ADMIN','MODERATOR')")
    public ResponseEntity<RegistrationWithRoomRequest> create(
            @RequestParam String registration,
            @RequestParam String room,
            @RequestParam(required = true) MultipartFile aadharPhoto,
            @RequestParam(required = true) MultipartFile documentUpload) throws IOException {

        // Parse JSON strings to objects
        Registration registrationDto = objectMapper.readValue(registration, Registration.class);
        RoomForRegistration roomDto = objectMapper.readValue(room, RoomForRegistration.class);

        // Convert files to byte arrays if present
        byte[] aadharPhotoBytes = aadharPhoto != null && !aadharPhoto.isEmpty() ? aadharPhoto.getBytes() : null;
        byte[] documentUploadBytes = documentUpload != null && !documentUpload.isEmpty() ? documentUpload.getBytes() : null;

        RegistrationWithRoomRequest created = registrationService.createWithFiles(
                registrationDto,
                roomDto,
                aadharPhotoBytes,
                documentUploadBytes);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/checkout")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<RegistrationWithRoomRequest> updateCheckOutDateById(@RequestBody UpdateUserForCheckOut updateUserForCheckOut) {
        return registrationService.updateCheckOutDateByID(updateUserForCheckOut.getRegistrationId(), updateUserForCheckOut.getCheckOutDate())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/checkoutAllMembers")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<String> checkOutAllDateById(@RequestBody Set<UpdateUserForCheckOut> userForCheckOutForAll) {
        return registrationService.checkoutAll(userForCheckOutForAll);
    }

    @PostMapping("/usersForClient")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER','GUEST')")
    public ResponseEntity<List<RegistrationWithRoomRequest>> findByColiveNameAndColiveUserName(@RequestBody GetUserInfoByClient getUserInfoByClient) {
        List<RegistrationWithRoomRequest> result = registrationService.findByColiveNameAndColiveUserName(getUserInfoByClient.getColiveUserName(), getUserInfoByClient.getColiveName());
        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
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
    public List<RegistrationWithRoomRequest> findAllByRoomNumber( @RequestParam String coliveUserName,
                                                   @RequestParam String coliveName,@PathVariable String roomNumber) {
        return registrationService.findAllByRoomNumber(coliveUserName,coliveName,roomNumber);
    }

    @GetMapping("/user/house/{houseNumber}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public List<RegistrationWithRoomRequest> findAllByHouseNumber(@RequestParam String coliveUserName,
                                                   @RequestParam String coliveName,@PathVariable String houseNumber) {
        return registrationService.findAllByHouseNumber(coliveUserName, coliveName,houseNumber);
    }


    @GetMapping("/user/getVacatedRoomsAndMembers")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public List<RegistrationWithRoomRequest> findRoomsWithVacated() {
        return registrationService.findRoomsWithVacatedaStatus();
    }

    /*@GetMapping("/user/room/{roomType}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public List<RegistrationWithRoomRequest> findAllByRoomType(@RequestParam String coliveUserName,
                                                @RequestParam String coliveName,@PathVariable String roomType) {
        return registrationService.findAllByRoomType(coliveUserName,coliveName,roomType);
    }*/

   /* @GetMapping("/user/house/{houseType}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public List<RegistrationWithRoomRequest> findAllByHouseType(@RequestParam String coliveUserName,
                                                 @RequestParam String coliveName,@PathVariable String houseType) {
        return registrationService.findAllByHouseType(coliveUserName,coliveName,houseType);
    }*/

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

    /**
     * Update member/tenant with file replacements
     * Handles deletion of old files and upload of new ones
     */
    @PutMapping("/updateUser/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN','MODERATOR')")
    public ResponseEntity<RegistrationWithRoomRequest> updateWithFiles(
            @PathVariable String id,
            @RequestParam String registration,
            @RequestParam String room,
            @RequestParam Boolean changingRoom,
            @RequestParam(required = false) MultipartFile newAadharPhoto,
            @RequestParam(required = false) MultipartFile newDocument) throws IOException {

        Registration registrationDto = objectMapper.readValue(registration, Registration.class);
        RoomForRegistration roomDto = objectMapper.readValue(room, RoomForRegistration.class);

        byte[] aadharPhotoBytes = newAadharPhoto != null && !newAadharPhoto.isEmpty() ? newAadharPhoto.getBytes() : null;
        byte[] documentBytes = newDocument != null && !newDocument.isEmpty() ? newDocument.getBytes() : null;

        return registrationService.updateWithFiles(id, registrationDto, roomDto, changingRoom, aadharPhotoBytes, documentBytes)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Download file for a member/tenant
     * Supports downloading aadhar photo or document
     */
    @GetMapping("/downloadFile/{registrationId}/{fileType}")
    @PreAuthorize("hasAnyRole('USER','ADMIN','MODERATOR')")
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable String registrationId,
            @PathVariable String fileType) {
        try {
            byte[] fileContent = registrationService.downloadFile(registrationId, fileType);
            String contentType = "aadhar".equalsIgnoreCase(fileType) ? "image/jpeg" : "application/pdf";
            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Content-Disposition", "attachment; filename=\"" + fileType + "\"")
                    .body(fileContent);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Check if file exists for a member/tenant
     */
    @GetMapping("/fileExists/{registrationId}/{fileType}")
    @PreAuthorize("hasAnyRole('USER','ADMIN','MODERATOR')")
    public ResponseEntity<Boolean> fileExists(
            @PathVariable String registrationId,
            @PathVariable String fileType) {
        return ResponseEntity.ok(registrationService.fileExists(registrationId, fileType));
    }

    /**
     * Delete all files for a member/tenant (called during member removal)
     */
    @DeleteMapping("/deleteFiles/{registrationId}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<Void> deleteRegistrationFiles(@PathVariable String registrationId) {
        if (!registrationService.existsById(registrationId)) {
            return ResponseEntity.notFound().build();
        }
        registrationService.deleteRegistrationFiles(registrationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Replace a single file (aadhar or document) for a member/tenant
     */
    @PostMapping("/replaceFile/{registrationId}/{fileType}")
    @PreAuthorize("hasAnyRole('USER','ADMIN','MODERATOR')")
    public ResponseEntity<String> replaceRegistrationFile(
            @PathVariable String registrationId,
            @PathVariable String fileType,
            @RequestParam MultipartFile file) throws IOException {
        try {
            String contentType = "aadhar".equalsIgnoreCase(fileType) ? "image/jpeg" : "application/pdf";
            String newPath = registrationService.replaceRegistrationFile(
                    registrationId, fileType, file.getBytes(), contentType);
            return ResponseEntity.ok(newPath);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get file information for a member/tenant
     * Returns both file paths and their existence status
     */
    @GetMapping("/fileInfo/{registrationId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN','MODERATOR')")
    public ResponseEntity<?> getRegistrationFileInfo(@PathVariable String registrationId) {
        try {
            return ResponseEntity.ok(registrationService.getRegistrationFileInfo(registrationId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get availability status for all rooms in a property
     * Used for dashboard display
     */
    @GetMapping("/rooms/availability/{coliveUserName}/{coliveName}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER','GUEST')")
    public ResponseEntity<List<RoomAvailabilityDTO>> getAllRoomsAvailability(
            @PathVariable String coliveUserName,
            @PathVariable String coliveName) {
        log.info("Getting availability for all rooms in {} owned by {}", coliveName, coliveUserName);

        try {
            List<RoomAvailabilityDTO> availability =
                roomAvailabilityService.getAllRoomsAvailability(coliveUserName, coliveName);

            return ResponseEntity.ok(availability);
        } catch (Exception e) {
            log.error("Error fetching room availability: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get availability for a specific room
     */
    @GetMapping("/rooms/availability/{coliveUserName}/{coliveName}/room/{roomNumber}")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER','GUEST')")
    public ResponseEntity<RoomAvailabilityDTO> getRoomAvailability(
            @PathVariable String coliveUserName,
            @PathVariable String coliveName,
            @PathVariable String roomNumber,
            @RequestParam(required = false, defaultValue = "1") Integer roomCapacity) {
        log.info("Getting availability for room {} in {}", roomNumber, coliveName);

        try {
            RoomAvailabilityDTO availability =
                roomAvailabilityService.calculateRoomAvailability(coliveName, coliveUserName,
                                                                  roomNumber, roomCapacity);

            return ResponseEntity.ok(availability);
        } catch (Exception e) {
            log.error("Error fetching room availability: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


}
