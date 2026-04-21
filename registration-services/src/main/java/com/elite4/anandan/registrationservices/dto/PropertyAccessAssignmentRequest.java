package com.elite4.anandan.registrationservices.dto;

import lombok.Data;

@Data
public class PropertyAccessAssignmentRequest {
    private String ownerUsername;
    private String coliveName;
    private String targetUsername;
    private String accessRole;
}
