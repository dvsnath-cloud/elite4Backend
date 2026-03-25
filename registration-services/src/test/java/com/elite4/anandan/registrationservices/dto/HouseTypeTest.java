package com.elite4.anandan.registrationservices.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HouseType enum safe parsing methods
 */
@DisplayName("HouseType Enum Parsing Tests")
class HouseTypeTest {

    @Test
    @DisplayName("fromValue should parse valid enum constant names")
    void testFromValueWithEnumConstant() {
        Optional<HouseType> result = HouseType.fromValue("ONE_BHK");
        assertTrue(result.isPresent());
        assertEquals(HouseType.ONE_BHK, result.get());
    }

    @Test
    @DisplayName("fromValue should parse valid display names (case-insensitive)")
    void testFromValueWithDisplayName() {
        Optional<HouseType> result = HouseType.fromValue("1 BHK");
        assertTrue(result.isPresent());
        assertEquals(HouseType.ONE_BHK, result.get());

        Optional<HouseType> result2 = HouseType.fromValue("2 BHK");
        assertTrue(result2.isPresent());
        assertEquals(HouseType.TWO_BHK, result2.get());

        Optional<HouseType> result3 = HouseType.fromValue("3 BHK");
        assertTrue(result3.isPresent());
        assertEquals(HouseType.THREE_BHK, result3.get());

        Optional<HouseType> result4 = HouseType.fromValue("1 RK");
        assertTrue(result4.isPresent());
        assertEquals(HouseType.ONE_RK, result4.get());
    }

    @Test
    @DisplayName("fromValue should handle null gracefully")
    void testFromValueWithNull() {
        Optional<HouseType> result = HouseType.fromValue(null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("fromValue should handle empty string")
    void testFromValueWithEmptyString() {
        Optional<HouseType> result = HouseType.fromValue("");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("fromValue should handle whitespace-only string")
    void testFromValueWithBlankString() {
        Optional<HouseType> result = HouseType.fromValue("   ");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("fromValue should handle invalid value")
    void testFromValueWithInvalidValue() {
        Optional<HouseType> result = HouseType.fromValue("INVALID_TYPE");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("fromValue should handle case-insensitive display names")
    void testFromValueCaseInsensitive() {
        Optional<HouseType> result1 = HouseType.fromValue("1 bhk");
        assertTrue(result1.isPresent());
        assertEquals(HouseType.ONE_BHK, result1.get());

        Optional<HouseType> result2 = HouseType.fromValue("1 BHK");
        assertTrue(result2.isPresent());
        assertEquals(HouseType.ONE_BHK, result2.get());

        Optional<HouseType> result3 = HouseType.fromValue("1 Bhk");
        assertTrue(result3.isPresent());
        assertEquals(HouseType.ONE_BHK, result3.get());
    }

    @Test
    @DisplayName("fromValueOrDefault should return parsed value when valid")
    void testFromValueOrDefaultWithValidValue() {
        HouseType result = HouseType.fromValueOrDefault("1 BHK", HouseType.TWO_BHK);
        assertEquals(HouseType.ONE_BHK, result);
    }

    @Test
    @DisplayName("fromValueOrDefault should return default value when null")
    void testFromValueOrDefaultWithNull() {
        HouseType result = HouseType.fromValueOrDefault(null, HouseType.TWO_BHK);
        assertEquals(HouseType.TWO_BHK, result);
    }

    @Test
    @DisplayName("fromValueOrDefault should return default value when invalid")
    void testFromValueOrDefaultWithInvalidValue() {
        HouseType result = HouseType.fromValueOrDefault("INVALID", HouseType.ONE_BHK);
        assertEquals(HouseType.ONE_BHK, result);
    }

    @Test
    @DisplayName("getDisplayName should return correct display name")
    void testGetDisplayName() {
        assertEquals("1 BHK", HouseType.ONE_BHK.getDisplayName());
        assertEquals("2 BHK", HouseType.TWO_BHK.getDisplayName());
        assertEquals("3 BHK", HouseType.THREE_BHK.getDisplayName());
        assertEquals("1 RK", HouseType.ONE_RK.getDisplayName());
    }
}

