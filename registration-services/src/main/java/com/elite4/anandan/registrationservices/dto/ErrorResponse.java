package com.elite4.anandan.registrationservices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error response DTO for all API errors
 * Used for consistent error response format across all endpoints
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private int status;                    // HTTP status code (e.g., 400, 401, 404, 500)
    private String message;                // Error message (e.g., "Bad Request - Validation Failed")
    private String error;                  // Error type/code (e.g., "USERNAME_ALREADY_EXISTS")
    private String details;                // Detailed error information
    private Map<String, String> fieldErrors;  // Field-level errors for validation
    private String path;                   // Request path
    private Instant timestamp;             // Error timestamp

    /**
     * Builder method for quick error creation
     */
    public static ErrorResponse badRequest(String message, String details) {
        return ErrorResponse.builder()
                .status(400)
                .message("Bad Request")
                .error(message)
                .details(details)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Builder method for unauthorized errors
     */
    public static ErrorResponse unauthorized(String message) {
        return ErrorResponse.builder()
                .status(401)
                .message("Unauthorized")
                .error(message)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Builder method for forbidden errors
     */
    public static ErrorResponse forbidden(String message) {
        return ErrorResponse.builder()
                .status(403)
                .message("Forbidden")
                .error(message)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Builder method for not found errors
     */
    public static ErrorResponse notFound(String message) {
        return ErrorResponse.builder()
                .status(404)
                .message("Not Found")
                .error(message)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Builder method for conflict errors
     */
    public static ErrorResponse conflict(String message) {
        return ErrorResponse.builder()
                .status(409)
                .message("Conflict")
                .error(message)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Builder method for validation errors
     */
    public static ErrorResponse validationError(String message, Map<String, String> fieldErrors) {
        return ErrorResponse.builder()
                .status(400)
                .message("Bad Request - Validation Failed")
                .error("VALIDATION_ERROR")
                .details(message)
                .fieldErrors(fieldErrors)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Builder method for internal server errors
     */
    public static ErrorResponse internalServerError(String message) {
        return ErrorResponse.builder()
                .status(500)
                .message("An unexpected error occurred")
                .error("INTERNAL_SERVER_ERROR")
                .details(message)
                .timestamp(Instant.now())
                .build();
    }
}

