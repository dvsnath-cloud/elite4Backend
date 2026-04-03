package com.elite4.anandan.registrationservices.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Objects;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoomForRegistration {
    private RoomType roomType;
    private String roomNumber;
    private String houseNumber;
    private int roomCapacity;
    private HouseType houseType;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoomForRegistration)) return false;
        RoomForRegistration that = (RoomForRegistration) o;
        return Objects.equals(trim(roomNumber), trim(that.roomNumber)) &&
                Objects.equals(trim(houseNumber), trim(that.houseNumber)) &&
                Objects.equals(roomType, that.roomType) &&
                Objects.equals(houseType, that.houseType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trim(roomNumber), trim(houseNumber), roomType, houseType);
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
