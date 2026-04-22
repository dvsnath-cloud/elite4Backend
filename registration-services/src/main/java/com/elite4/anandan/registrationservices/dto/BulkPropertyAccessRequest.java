package com.elite4.anandan.registrationservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for bulk-assigning a single user to multiple properties in one call.
 * The targetUsername is applied to every assignment in the list.
 */
public class BulkPropertyAccessRequest {

    @NotBlank(message = "targetUsername is required")
    private String targetUsername;

    @NotEmpty(message = "At least one assignment is required")
    private List<PropertyAccessAssignmentRequest> assignments;

    public String getTargetUsername() { return targetUsername; }
    public void setTargetUsername(String targetUsername) { this.targetUsername = targetUsername; }

    public List<PropertyAccessAssignmentRequest> getAssignments() { return assignments; }
    public void setAssignments(List<PropertyAccessAssignmentRequest> assignments) { this.assignments = assignments; }
}
