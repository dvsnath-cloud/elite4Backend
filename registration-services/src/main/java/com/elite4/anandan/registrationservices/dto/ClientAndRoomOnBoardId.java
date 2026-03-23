package com.elite4.anandan.registrationservices.dto;

import jakarta.validation.Valid;
import lombok.Data;

@Data
public class ClientAndRoomOnBoardId {
    private String clientName;
    private String roomOnBoardId;
    private String clientCategory;
    @Valid
    private BankDetails bankDetails;
}
