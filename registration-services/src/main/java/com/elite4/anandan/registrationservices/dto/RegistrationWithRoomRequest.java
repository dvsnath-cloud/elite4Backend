package com.elite4.anandan.registrationservices.dto;

import lombok.Data;

@Data
public class RegistrationWithRoomRequest {
    private Registration registration;
    private Room room;
    private String id;
}
