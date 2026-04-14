# Implementation Code - Room Availability Feature

## File 1: RoomAvailabilityDTO.java
**Location:** `registration-services/src/main/java/com/elite4/anandan/registrationservices/dto/RoomAvailabilityDTO.java`

```java
package com.elite4.anandan.registrationservices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for room availability status used in dashboard
 * Provides real-time occupancy information based on room capacity and active members
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoomAvailabilityDTO {
    
    /**
     * CoLive/Property name
     */
    private String coliveName;
    
    /**
     * Room number (for PG/Hostel)
     */
    private String roomNumber;
    
    /**
     * House number (for Houses/Flats)
     */
    private String houseNumber;
    
    /**
     * Total capacity of the room
     */
    private Integer roomCapacity;
    
    /**
     * Current number of active (non-checked-out) members
     */
    private Integer currentOccupants;
    
    /**
     * Availability status: AVAILABLE, PARTIALLY_OCCUPIED, OCCUPIED
     */
    private String availabilityStatus;
    
    /**
     * Occupancy percentage (0-100)
     */
    private Double occupancyPercentage;
    
    /**
     * Room type (SINGLE, DOUBLE, TRIPLE, etc.)
     */
    private String roomType;
    
    /**
     * House type (ONE_RK, TWO_RK, THREE_RK, etc.)
     */
    private String houseType;
    
    /**
     * Category type (PG, HOUSE, FLAT, HOSTEL)
     */
    private String categoryType;
}
```

---

## File 2: Updated Room.java (Add PARTIALLY_OCCUPIED if not exists)
**Location:** `registration-services/src/main/java/com/elite4/anandan/registrationservices/dto/Room.java`

Check if `roomCapacity` field exists. If not, add it:

```java
// Add this field to Room class if it doesn't exist
private int roomCapacity;  // How many members can stay in this room

// Update the enum if PARTIALLY_OCCUPIED doesn't exist
public enum roomOccupied {
    NOT_OCCUPIED,
    PARTIALLY_OCCUPIED,  // Add if not exists
    OCCUPIED,
    VACATED
}
```

---

## File 3: Updated RegistrationRepository.java
**Location:** `registration-services/src/main/java/com/elite4/anandan/registrationservices/repository/RegistrationRepository.java`

Add these query methods:

```java
// Count active (not checked out) occupants in a room
@Query(value = "{ 'coliveName': ?0, 'coliveUserName': ?1, 'room.roomNumber': ?2, " +
       "'occupied': { $in: ['OCCUPIED', 'PARTIALLY_OCCUPIED'] }, " +
       "$or': [{ 'checkOutDate': null }, { 'checkOutDate': { $gt: new Date() }}] }")
List<RegistrationDocument> findActiveOccupantsInRoom(String coliveName, String coliveUserName, String roomNumber);

// Count active occupants in a house
@Query(value = "{ 'coliveName': ?0, 'coliveUserName': ?1, 'room.houseNumber': ?2, " +
       "'occupied': { $in: ['OCCUPIED', 'PARTIALLY_OCCUPIED'] }, " +
       "$or': [{ 'checkOutDate': null }, { 'checkOutDate': { $gt: new Date() }}] }")
List<RegistrationDocument> findActiveOccupantsInHouse(String coliveName, String coliveUserName, String houseNumber);
```

---

## File 4: RoomAvailabilityService.java (NEW)
**Location:** `registration-services/src/main/java/com/elite4/anandan/registrationservices/service/RoomAvailabilityService.java`

```java
package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.document.RoomOnBoardDocument;
import com.elite4.anandan.registrationservices.dto.Room;
import com.elite4.anandan.registrationservices.dto.RoomAvailabilityDTO;
import com.elite4.anandan.registrationservices.model.User;
import com.elite4.anandan.registrationservices.dto.ClientAndRoomOnBoardId;
import com.elite4.anandan.registrationservices.document.RegistrationDocument;
import com.elite4.anandan.registrationservices.repository.RegistrationRepository;
import com.elite4.anandan.registrationservices.repository.RoomsOrHouseRepository;
import com.elite4.anandan.registrationservices.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating and managing room availability status
 * Used for dashboard display and room management
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RoomAvailabilityService {
    
    private final RegistrationRepository registrationRepository;
    private final RoomsOrHouseRepository roomsOrHouseRepository;
    private final UserRepository userRepository;

    /**
     * Calculate availability status for a single room
     * 
     * @param coliveName - Name of the property
     * @param coliveUserName - Username of property owner
     * @param roomNumber - Room number to check
     * @param roomCapacity - Total capacity of the room
     * @return RoomAvailabilityDTO with current occupancy status
     */
    public RoomAvailabilityDTO calculateRoomAvailability(String coliveName, String coliveUserName, 
                                                         String roomNumber, Integer roomCapacity) {
        log.info("Calculating availability for room {} in {} owned by {}", 
                 roomNumber, coliveName, coliveUserName);
        
        try {
            // Find all active registrations for this room
            List<RegistrationDocument> allRegistrations = 
                registrationRepository.findByColiveNameAndColiveUserNameAndRoomRoomNumber(
                    coliveName, coliveUserName, roomNumber);
            
            // Filter to only active (not checked out) members
            List<RegistrationDocument> activeMembers = allRegistrations.stream()
                .filter(reg -> reg.getCheckOutDate() == null || reg.getCheckOutDate().after(new Date()))
                .collect(Collectors.toList());
            
            int currentOccupants = activeMembers.size();
            
            // Default capacity to 1 if not provided
            if (roomCapacity == null || roomCapacity <= 0) {
                roomCapacity = 1;
            }
            
            // Determine availability status
            String status = determineAvailabilityStatus(currentOccupants, roomCapacity);
            double occupancyPercentage = calculateOccupancyPercentage(currentOccupants, roomCapacity);
            
            RoomAvailabilityDTO dto = RoomAvailabilityDTO.builder()
                .coliveName(coliveName)
                .roomNumber(roomNumber)
                .roomCapacity(roomCapacity)
                .currentOccupants(currentOccupants)
                .availabilityStatus(status)
                .occupancyPercentage(occupancyPercentage)
                .build();
            
            log.info("Room {} availability: {} ({} / {} members)", 
                     roomNumber, status, currentOccupants, roomCapacity);
            
            return dto;
        } catch (Exception e) {
            log.error("Error calculating availability for room {}: {}", roomNumber, e.getMessage(), e);
            return RoomAvailabilityDTO.builder()
                .coliveName(coliveName)
                .roomNumber(roomNumber)
                .availabilityStatus("ERROR")
                .build();
        }
    }

    /**
     * Calculate availability status for a house/flat
     * 
     * @param coliveName - Name of the property
     * @param coliveUserName - Username of property owner
     * @param houseNumber - House number to check
     * @param roomCapacity - Total capacity
     * @return RoomAvailabilityDTO with current occupancy status
     */
    public RoomAvailabilityDTO calculateHouseAvailability(String coliveName, String coliveUserName,
                                                          String houseNumber, Integer roomCapacity) {
        log.info("Calculating availability for house {} in {} owned by {}", 
                 houseNumber, coliveName, coliveUserName);
        
        try {
            // Find all active registrations for this house
            List<RegistrationDocument> allRegistrations = 
                registrationRepository.findByColiveNameAndColiveUserNameAndRoomHouseNumber(
                    coliveName, coliveUserName, houseNumber);
            
            // Filter to only active members
            List<RegistrationDocument> activeMembers = allRegistrations.stream()
                .filter(reg -> reg.getCheckOutDate() == null || reg.getCheckOutDate().after(new Date()))
                .collect(Collectors.toList());
            
            int currentOccupants = activeMembers.size();
            
            if (roomCapacity == null || roomCapacity <= 0) {
                roomCapacity = 1;
            }
            
            String status = determineAvailabilityStatus(currentOccupants, roomCapacity);
            double occupancyPercentage = calculateOccupancyPercentage(currentOccupants, roomCapacity);
            
            RoomAvailabilityDTO dto = RoomAvailabilityDTO.builder()
                .coliveName(coliveName)
                .houseNumber(houseNumber)
                .roomCapacity(roomCapacity)
                .currentOccupants(currentOccupants)
                .availabilityStatus(status)
                .occupancyPercentage(occupancyPercentage)
                .build();
            
            log.info("House {} availability: {} ({} / {} members)", 
                     houseNumber, status, currentOccupants, roomCapacity);
            
            return dto;
        } catch (Exception e) {
            log.error("Error calculating availability for house {}: {}", houseNumber, e.getMessage(), e);
            return RoomAvailabilityDTO.builder()
                .coliveName(coliveName)
                .houseNumber(houseNumber)
                .availabilityStatus("ERROR")
                .build();
        }
    }

    /**
     * Get availability status for all rooms in a property
     * 
     * @param coliveUserName - Property owner username
     * @param coliveName - Property name
     * @return List of RoomAvailabilityDTO for all rooms
     */
    public List<RoomAvailabilityDTO> getAllRoomsAvailability(String coliveUserName, String coliveName) {
        log.info("Getting all rooms availability for {} owned by {}", coliveName, coliveUserName);
        
        List<RoomAvailabilityDTO> availabilityList = new ArrayList<>();
        
        try {
            // Fetch user and their properties
            Optional<User> userOpt = userRepository.findByUsername(coliveUserName);
            if (userOpt.isEmpty()) {
                log.warn("User not found: {}", coliveUserName);
                return availabilityList;
            }
            
            User user = userOpt.get();
            Set<ClientAndRoomOnBoardId> clientDetails = user.getClientDetails();
            
            if (clientDetails == null || clientDetails.isEmpty()) {
                log.warn("No properties found for user: {}", coliveUserName);
                return availabilityList;
            }
            
            // Find the specific property
            for (ClientAndRoomOnBoardId client : clientDetails) {
                if (client.getColiveName().equals(coliveName)) {
                    Optional<RoomOnBoardDocument> roomDocOpt = 
                        roomsOrHouseRepository.findById(client.getRoomOnBoardId());
                    
                    if (roomDocOpt.isPresent()) {
                        Set<Room> rooms = roomDocOpt.get().getRooms();
                        
                        if (rooms != null && !rooms.isEmpty()) {
                            for (Room room : rooms) {
                                RoomAvailabilityDTO dto = null;
                                
                                // Handle room (with roomNumber)
                                if (room.getRoomNumber() != null && !room.getRoomNumber().isBlank()) {
                                    dto = calculateRoomAvailability(coliveName, coliveUserName,
                                            room.getRoomNumber(), room.getRoomCapacity());
                                    if (room.getRoomType() != null) {
                                        dto.setRoomType(room.getRoomType().toString());
                                    }
                                }
                                // Handle house (with houseNumber)
                                else if (room.getHouseNumber() != null && !room.getHouseNumber().isBlank()) {
                                    dto = calculateHouseAvailability(coliveName, coliveUserName,
                                            room.getHouseNumber(), room.getRoomCapacity());
                                    if (room.getHouseType() != null) {
                                        dto.setHouseType(room.getHouseType().toString());
                                    }
                                }
                                
                                if (dto != null) {
                                    dto.setCategoryType(client.getClientCategory());
                                    availabilityList.add(dto);
                                }
                            }
                        }
                    }
                    break;
                }
            }
            
            log.info("Retrieved availability for {} rooms in {}", availabilityList.size(), coliveName);
            
        } catch (Exception e) {
            log.error("Error getting all rooms availability for {}: {}", coliveName, e.getMessage(), e);
        }
        
        return availabilityList;
    }

    /**
     * Get a summary of all properties with their overall occupancy
     * 
     * @param coliveUserName - Property owner username
     * @return List of property summaries
     */
    public List<PropertyOccupancySummaryDTO> getPropertyOccupancySummary(String coliveUserName) {
        log.info("Getting occupancy summary for all properties of user: {}", coliveUserName);
        
        List<PropertyOccupancySummaryDTO> summaryList = new ArrayList<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(coliveUserName);
            if (userOpt.isEmpty()) {
                log.warn("User not found: {}", coliveUserName);
                return summaryList;
            }
            
            User user = userOpt.get();
            Set<ClientAndRoomOnBoardId> clientDetails = user.getClientDetails();
            
            if (clientDetails == null || clientDetails.isEmpty()) {
                return summaryList;
            }
            
            for (ClientAndRoomOnBoardId client : clientDetails) {
                List<RoomAvailabilityDTO> roomsAvailability = 
                    getAllRoomsAvailability(coliveUserName, client.getColiveName());
                
                int totalCapacity = roomsAvailability.stream()
                    .mapToInt(r -> r.getRoomCapacity() != null ? r.getRoomCapacity() : 0)
                    .sum();
                
                int totalOccupants = roomsAvailability.stream()
                    .mapToInt(r -> r.getCurrentOccupants() != null ? r.getCurrentOccupants() : 0)
                    .sum();
                
                long fullyOccupied = roomsAvailability.stream()
                    .filter(r -> "OCCUPIED".equals(r.getAvailabilityStatus()))
                    .count();
                
                long partiallyOccupied = roomsAvailability.stream()
                    .filter(r -> "PARTIALLY_OCCUPIED".equals(r.getAvailabilityStatus()))
                    .count();
                
                long available = roomsAvailability.stream()
                    .filter(r -> "AVAILABLE".equals(r.getAvailabilityStatus()))
                    .count();
                
                PropertyOccupancySummaryDTO summary = PropertyOccupancySummaryDTO.builder()
                    .coliveName(client.getColiveName())
                    .categoryType(client.getClientCategory())
                    .totalRooms(roomsAvailability.size())
                    .totalCapacity(totalCapacity)
                    .totalOccupants(totalOccupants)
                    .fullyOccupiedCount((int) fullyOccupied)
                    .partiallyOccupiedCount((int) partiallyOccupied)
                    .availableCount((int) available)
                    .overallOccupancyPercentage(totalCapacity > 0 ? 
                        (double) totalOccupants / totalCapacity * 100 : 0)
                    .build();
                
                summaryList.add(summary);
            }
            
            log.info("Generated summary for {} properties", summaryList.size());
            
        } catch (Exception e) {
            log.error("Error getting occupancy summary: {}", e.getMessage(), e);
        }
        
        return summaryList;
    }

    /**
     * Determine availability status based on occupancy
     * 
     * AVAILABLE: 0 occupants
     * PARTIALLY_OCCUPIED: 1+ and < capacity
     * OCCUPIED: >= capacity
     */
    private String determineAvailabilityStatus(int currentOccupants, int roomCapacity) {
        if (currentOccupants == 0) {
            return "AVAILABLE";
        } else if (currentOccupants >= roomCapacity) {
            return "OCCUPIED";
        } else {
            return "PARTIALLY_OCCUPIED";
        }
    }

    /**
     * Calculate occupancy percentage
     */
    private double calculateOccupancyPercentage(int currentOccupants, int roomCapacity) {
        if (roomCapacity <= 0) {
            return 0;
        }
        return Math.round(((double) currentOccupants / roomCapacity * 100) * 100.0) / 100.0;
    }
}

/**
 * DTO for property-level occupancy summary
 */
@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
class PropertyOccupancySummaryDTO {
    private String coliveName;
    private String categoryType;
    private Integer totalRooms;
    private Integer totalCapacity;
    private Integer totalOccupants;
    private Integer fullyOccupiedCount;
    private Integer partiallyOccupiedCount;
    private Integer availableCount;
    private Double overallOccupancyPercentage;
}
```

---

## File 5: Updated RegistrationController.java
**Location:** Add these endpoints to `registration-services/src/main/java/com/elite4/anandan/registrationservices/controller/RegistrationController.java`

```java
// Add this import
import com.elite4.anandan.registrationservices.service.RoomAvailabilityService;
import com.elite4.anandan.registrationservices.dto.RoomAvailabilityDTO;

// Inject RoomAvailabilityService in the controller
private final RoomAvailabilityService roomAvailabilityService;

// Add these endpoints

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

/**
 * Get occupancy summary for all properties
 */
@GetMapping("/rooms/occupancy-summary/{coliveUserName}")
@PreAuthorize("hasAnyRole('ADMIN','MODERATOR','USER')")
public ResponseEntity<?> getOccupancySummary(@PathVariable String coliveUserName) {
    log.info("Getting occupancy summary for user: {}", coliveUserName);
    
    try {
        var summary = roomAvailabilityService.getPropertyOccupancySummary(coliveUserName);
        return ResponseEntity.ok(summary);
    } catch (Exception e) {
        log.error("Error fetching occupancy summary: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
```

---

## File 6: Updated Checkout Logic in RegistrationService.java

Add this to the checkout method after updating the checkout date:

```java
/**
 * Update room occupancy status after checkout
 * Called after a member is checked out
 */
private void updateRoomOccupancyAfterCheckout(RegistrationDocument registrationDoc) {
    try {
        // Find the RoomOnBoardDocument
        Optional<User> userOpt = userRepository.findByUsername(registrationDoc.getColiveUserName());
        if (userOpt.isEmpty()) {
            log.warn("User not found for occupancy update: {}", registrationDoc.getColiveUserName());
            return;
        }
        
        User user = userOpt.get();
        Set<ClientAndRoomOnBoardId> clientDetails = user.getClientDetails();
        
        for (ClientAndRoomOnBoardId client : clientDetails) {
            if (client.getColiveName().equals(registrationDoc.getColiveName())) {
                Optional<RoomOnBoardDocument> roomDocOpt = 
                    roomsOrHouseRepository.findById(client.getRoomOnBoardId());
                
                if (roomDocOpt.isPresent()) {
                    RoomOnBoardDocument roomDoc = roomDocOpt.get();
                    Set<Room> rooms = roomDoc.getRooms();
                    
                    String roomIdentifier = registrationDoc.getRoomForRegistration() != null ? 
                        registrationDoc.getRoomForRegistration().getRoomNumber() :
                        registrationDoc.getRoomForRegistration().getHouseNumber();
                    
                    for (Room room : rooms) {
                        boolean isMatch = false;
                        
                        if (room.getRoomNumber() != null && 
                            room.getRoomNumber().equals(roomIdentifier)) {
                            isMatch = true;
                        } else if (room.getHouseNumber() != null &&
                                   room.getHouseNumber().equals(roomIdentifier)) {
                            isMatch = true;
                        }
                        
                        if (isMatch) {
                            // Recalculate occupancy
                            List<RegistrationDocument> activeMembers = 
                                registrationRepository.findByColiveNameAndColiveUserNameAndRoomRoomNumberAndOccupied(
                                    registrationDoc.getColiveName(),
                                    registrationDoc.getColiveUserName(),
                                    roomIdentifier,
                                    Registration.roomOccupied.OCCUPIED);
                            
                            int count = (int) activeMembers.stream()
                                .filter(r -> r.getCheckOutDate() == null || 
                                           r.getCheckOutDate().after(new Date()))
                                .count();
                            
                            // Update room occupancy status
                            if (count == 0) {
                                room.setOccupied(Room.roomOccupied.NOT_OCCUPIED);
                                log.info("Room {} is now AVAILABLE", roomIdentifier);
                            } else if (count < room.getRoomCapacity()) {
                                room.setOccupied(Room.roomOccupied.PARTIALLY_OCCUPIED);
                                log.info("Room {} is now PARTIALLY_OCCUPIED ({}/{})", 
                                        roomIdentifier, count, room.getRoomCapacity());
                            } else {
                                room.setOccupied(Room.roomOccupied.OCCUPIED);
                                log.info("Room {} is OCCUPIED ({}/{})", 
                                        roomIdentifier, count, room.getRoomCapacity());
                            }
                            
                            roomsOrHouseRepository.save(roomDoc);
                            return;
                        }
                    }
                }
            }
        }
    } catch (Exception e) {
        log.error("Error updating room occupancy after checkout: {}", e.getMessage(), e);
    }
}
```

Then call this method in your checkout logic:
```java
// After updating checkOutDate in your checkout method
updateRoomOccupancyAfterCheckout(registrationDoc);
```

---

## Summary of Changes

### New Files to Create:
1. ✅ RoomAvailabilityDTO.java
2. ✅ RoomAvailabilityService.java

### Files to Update:
1. ✅ Room.java - Add `roomCapacity` field and `PARTIALLY_OCCUPIED` enum value
2. ✅ RegistrationRepository.java - Add new query methods
3. ✅ RegistrationController.java - Add 3 new endpoints
4. ✅ RegistrationService.java - Add room occupancy update logic

### Key Features:
- Real-time room availability calculation
- Support for both rooms (roomNumber) and houses (houseNumber)
- Occupancy percentage tracking
- Property-level summary dashboard
- Automatic status updates on checkout

