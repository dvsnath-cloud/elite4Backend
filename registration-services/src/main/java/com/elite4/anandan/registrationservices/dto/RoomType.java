package com.elite4.anandan.registrationservices.dto;

/**
 * Enum representing different room types available in the system.
 */
public enum RoomType {
    SINGLE("Single Occupancy"),
    DOUBLE("Double Occupancy"),
    TRIPLE("Triple Occupancy"),
    FOUR("Four Occupancy");

    private final String displayName;

    RoomType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
