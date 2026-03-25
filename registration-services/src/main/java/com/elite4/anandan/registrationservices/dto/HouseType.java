package com.elite4.anandan.registrationservices.dto;

import java.util.Optional;

/**
 * Enum representing different house types available in the system.
 */
public enum HouseType {
    ONE_RK("1 RK"),
    TWO_BHK("2 BHK"),
    THREE_BHK("3 BHK"),
    ONE_BHK("1 BHK");

    private final String displayName;

    HouseType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Safely parse a string value to HouseType enum
     * Handles null, empty strings, display names, and enum constants
     *
     * @param value the string value to parse
     * @return Optional containing the enum value if found, empty otherwise
     */
    public static Optional<HouseType> fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }

        String trimmedValue = value.trim();

        // Try to match by enum constant name
        try {
            return Optional.of(HouseType.valueOf(trimmedValue));
        } catch (IllegalArgumentException e) {
            // Continue to try matching by display name
        }

        // Try to match by display name
        for (HouseType type : HouseType.values()) {
            if (type.displayName.equalsIgnoreCase(trimmedValue)) {
                return Optional.of(type);
            }
        }

        return Optional.empty();
    }

    /**
     * Parse a string value to HouseType enum, returning a default value if not found
     *
     * @param value the string value to parse
     * @param defaultValue the default value to return if parsing fails
     * @return the parsed enum value or default value
     */
    public static HouseType fromValueOrDefault(String value, HouseType defaultValue) {
        return fromValue(value).orElse(defaultValue);
    }
}
