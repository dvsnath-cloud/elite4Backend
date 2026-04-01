package com.elite4.anandan.registrationservices.service;

import com.elite4.anandan.registrationservices.document.RoomOnBoardDocument;
import com.elite4.anandan.registrationservices.dto.Registration;
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
     * @return RoomAvailabilityDTO with current occupancy status and gender counts
     */
    public RoomAvailabilityDTO calculateRoomAvailability(String coliveName, String coliveUserName,
                                                         String roomNumber, Integer roomCapacity) {
        log.info("Calculating availability for room {} in {} owned by {}",
                 roomNumber, coliveName, coliveUserName);

        try {
            // Find all registrations for this room
            List<RegistrationDocument> allRegistrations =
                registrationRepository.findByColiveNameAndColiveUserNameAndRoomForRegistrationRoomNumber(
                    coliveName, coliveUserName, roomNumber);

            // Filter to only active (not checked out) members
            List<RegistrationDocument> activeMembers = allRegistrations.stream()
                .filter(reg -> reg.getCheckOutDate() == null || reg.getCheckOutDate().after(new Date()))
                .collect(Collectors.toList());

            int currentOccupants = activeMembers.size();

            // Count male and female tenants
            long maleCount = activeMembers.stream()
                .filter(reg -> reg.getGender() != null &&
                        reg.getGender().equals(Registration.Gender.MALE))
                .count();

            long femaleCount = activeMembers.stream()
                .filter(reg -> reg.getGender() != null &&
                        reg.getGender().equals(Registration.Gender.FEMALE))
                .count();

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
                .maleTenantsCount((int) maleCount)
                .femaleTenantsCount((int) femaleCount)
                .build();

            log.info("Room {} availability: {} ({} / {} members) - Males: {}, Females: {}",
                     roomNumber, status, currentOccupants, roomCapacity, maleCount, femaleCount);

            return dto;
        } catch (Exception e) {
            log.error("Error calculating availability for room {}: {}", roomNumber, e.getMessage(), e);
            return RoomAvailabilityDTO.builder()
                .coliveName(coliveName)
                .roomNumber(roomNumber)
                .availabilityStatus("ERROR")
                .maleTenantsCount(0)
                .femaleTenantsCount(0)
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
     * @return RoomAvailabilityDTO with current occupancy status and gender counts
     */
    public RoomAvailabilityDTO calculateHouseAvailability(String coliveName, String coliveUserName,
                                                          String houseNumber, Integer roomCapacity) {
        log.info("Calculating availability for house {} in {} owned by {}",
                 houseNumber, coliveName, coliveUserName);

        try {
            // Find all registrations for this house
            List<RegistrationDocument> allRegistrations =
                registrationRepository.findByColiveNameAndColiveUserNameAndRoomForRegistrationHouseNumber(
                    coliveName, coliveUserName, houseNumber);

            // Filter to only active members
            List<RegistrationDocument> activeMembers = allRegistrations.stream()
                .filter(reg -> reg.getCheckOutDate() == null || reg.getCheckOutDate().after(new Date()))
                .collect(Collectors.toList());

            int currentOccupants = activeMembers.size();

            // Count male and female tenants
            long maleCount = activeMembers.stream()
                .filter(reg -> reg.getGender() != null &&
                        reg.getGender().equals(Registration.Gender.MALE))
                .count();

            long femaleCount = activeMembers.stream()
                .filter(reg -> reg.getGender() != null &&
                        reg.getGender().equals(Registration.Gender.FEMALE))
                .count();

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
                .maleTenantsCount((int) maleCount)
                .femaleTenantsCount((int) femaleCount)
                .build();

            log.info("House {} availability: {} ({} / {} members) - Males: {}, Females: {}",
                     houseNumber, status, currentOccupants, roomCapacity, maleCount, femaleCount);

            return dto;
        } catch (Exception e) {
            log.error("Error calculating availability for house {}: {}", houseNumber, e.getMessage(), e);
            return RoomAvailabilityDTO.builder()
                .coliveName(coliveName)
                .houseNumber(houseNumber)
                .availabilityStatus("ERROR")
                .maleTenantsCount(0)
                .femaleTenantsCount(0)
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

