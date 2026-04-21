package com.elite4.anandan.registrationservices.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PropertyAccessAssignmentResponse {
    private String id;
    private String username;
    private String displayName;
    private String email;
    private String phone;
    private boolean active;
    private String ownerUsername;
    private String coliveName;
    private String accessRole;
    private boolean ownerAssignment;
}
