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
     * Current number of active (non-checked out) members
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

    /**
     * Count of male tenants in the room
     */
    private Integer maleTenantsCount;

    /**
     * Count of female tenants in the room
     */
    private Integer femaleTenantsCount;
}

