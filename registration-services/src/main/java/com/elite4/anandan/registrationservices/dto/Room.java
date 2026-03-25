package com.elite4.anandan.registrationservices.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Objects;

@Data
public class Room {
    private RoomType roomType;
    private String roomNumber;
    private String houseNumber;
    private HouseType houseType;
    private roomOccupied occupied = roomOccupied.NOT_OCCUPIED;

    public enum roomOccupied {
        OCCUPIED, NOT_OCCUPIED, PARTIALLY_OCCUPIED
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Room)) return false;
        Room that = (Room) o;
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
