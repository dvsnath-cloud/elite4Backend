package com.elite4.anandan.registrationservices.dto;

import lombok.Data;
import java.util.Date;

@Data
public class UpdateUserForCheckOut {
    private String registrationId;
    private Date checkOutDate;
    private Registration.roomOccupied occupied;
}
