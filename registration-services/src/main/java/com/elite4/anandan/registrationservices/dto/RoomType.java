package com.elite4.anandan.registrationservices.dto;

import java.util.Optional;

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

    /**
     * Safely parse a string value to RoomType enum
     * Handles null, empty strings, display names, and enum constants
     *
     * @param value the string value to parse
     * @return Optional containing the enum value if found, empty otherwise
     */
    public static Optional<RoomType> fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }

        String trimmedValue = value.trim();

        // Try to match by enum constant name
        try {
            return Optional.of(RoomType.valueOf(trimmedValue));
        } catch (IllegalArgumentException e) {
            // Continue to try matching by display name
        }

        // Try to match by display name
        for (RoomType type : RoomType.values()) {
            if (type.displayName.equalsIgnoreCase(trimmedValue)) {
                return Optional.of(type);
            }
        }

        return Optional.empty();
    }

    /**
     * Parse a string value to RoomType enum, returning a default value if not found
     *
     * @param value the string value to parse
     * @param defaultValue the default value to return if parsing fails
     * @return the parsed enum value or default value
     */
    public static RoomType fromValueOrDefault(String value, RoomType defaultValue) {
        return fromValue(value).orElse(defaultValue);
    }
}
