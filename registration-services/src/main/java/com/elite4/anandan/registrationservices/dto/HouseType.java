package com.elite4.anandan.registrationservices.dto;

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
}
