package com.elite4.anandan.registrationservices.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.Date;

@Data
@AllArgsConstructor
public class UpdateUserForCheckOut {
    private String registrationId;
    private Date checkOutDate;
}
