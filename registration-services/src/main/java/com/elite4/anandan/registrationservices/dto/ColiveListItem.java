package com.elite4.anandan.registrationservices.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Lightweight DTO for listing available CoLive properties in the transfer flow.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ColiveListItem {
    private String coliveUserName;
    private String coliveName;
    private String categoryType;
    private List<RoomListItem> availableRooms;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomListItem {
        private String roomNumber;
        private String houseNumber;
        private String roomType;
        private String houseType;
        private String occupied;
    }
}
