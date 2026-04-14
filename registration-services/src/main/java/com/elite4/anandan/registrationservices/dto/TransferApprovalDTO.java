package com.elite4.anandan.registrationservices.dto;

import lombok.Data;

@Data
public class TransferApprovalDTO {
    private Double roomRent;
    private Double advanceAmount;
    private String toRoomNumber;
    private String toHouseNumber;
}
