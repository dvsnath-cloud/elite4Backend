package com.elite4.anandan.registrationservices.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RoomType enum safe parsing methods
 */
@DisplayName("RoomType Enum Parsing Tests")
class RoomTypeTest {

    @Test
    @DisplayName("fromValue should parse valid enum constant names")
    void testFromValueWithEnumConstant() {
        Optional<RoomType> result = RoomType.fromValue("SINGLE");
        assertTrue(result.isPresent());
        assertEquals(RoomType.SINGLE, result.get());
    }

    @Test
    @DisplayName("fromValue should parse valid display names (case-insensitive)")
    void testFromValueWithDisplayName() {
        Optional<RoomType> result = RoomType.fromValue("Single Occupancy");
        assertTrue(result.isPresent());
        assertEquals(RoomType.SINGLE, result.get());

        Optional<RoomType> result2 = RoomType.fromValue("Double Occupancy");
        assertTrue(result2.isPresent());
        assertEquals(RoomType.DOUBLE, result2.get());

        Optional<RoomType> result3 = RoomType.fromValue("Triple Occupancy");
        assertTrue(result3.isPresent());
        assertEquals(RoomType.TRIPLE, result3.get());

        Optional<RoomType> result4 = RoomType.fromValue("Four Occupancy");
        assertTrue(result4.isPresent());
        assertEquals(RoomType.FOUR, result4.get());
    }

    @Test
    @DisplayName("fromValue should handle null gracefully")
    void testFromValueWithNull() {
        Optional<RoomType> result = RoomType.fromValue(null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("fromValue should handle empty string")
    void testFromValueWithEmptyString() {
        Optional<RoomType> result = RoomType.fromValue("");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("fromValue should handle whitespace-only string")
    void testFromValueWithBlankString() {
        Optional<RoomType> result = RoomType.fromValue("   ");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("fromValue should handle invalid value")
    void testFromValueWithInvalidValue() {
        Optional<RoomType> result = RoomType.fromValue("INVALID_ROOM_TYPE");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("fromValue should handle case-insensitive display names")
    void testFromValueCaseInsensitive() {
        Optional<RoomType> result1 = RoomType.fromValue("single occupancy");
        assertTrue(result1.isPresent());
        assertEquals(RoomType.SINGLE, result1.get());

        Optional<RoomType> result2 = RoomType.fromValue("Single Occupancy");
        assertTrue(result2.isPresent());
        assertEquals(RoomType.SINGLE, result2.get());

        Optional<RoomType> result3 = RoomType.fromValue("SINGLE OCCUPANCY");
        assertTrue(result3.isPresent());
        assertEquals(RoomType.SINGLE, result3.get());
    }

    @Test
    @DisplayName("fromValueOrDefault should return parsed value when valid")
    void testFromValueOrDefaultWithValidValue() {
        RoomType result = RoomType.fromValueOrDefault("Double Occupancy", RoomType.SINGLE);
        assertEquals(RoomType.DOUBLE, result);
    }

    @Test
    @DisplayName("fromValueOrDefault should return default value when null")
    void testFromValueOrDefaultWithNull() {
        RoomType result = RoomType.fromValueOrDefault(null, RoomType.DOUBLE);
        assertEquals(RoomType.DOUBLE, result);
    }

    @Test
    @DisplayName("fromValueOrDefault should return default value when invalid")
    void testFromValueOrDefaultWithInvalidValue() {
        RoomType result = RoomType.fromValueOrDefault("INVALID", RoomType.SINGLE);
        assertEquals(RoomType.SINGLE, result);
    }

    @Test
    @DisplayName("getDisplayName should return correct display name")
    void testGetDisplayName() {
        assertEquals("Single Occupancy", RoomType.SINGLE.getDisplayName());
        assertEquals("Double Occupancy", RoomType.DOUBLE.getDisplayName());
        assertEquals("Triple Occupancy", RoomType.TRIPLE.getDisplayName());
        assertEquals("Four Occupancy", RoomType.FOUR.getDisplayName());
    }
}

