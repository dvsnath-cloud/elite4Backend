package com.elite4.anandan.registrationservices.exception;

import com.mongodb.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for handling validation errors and other exceptions.
 * Note: Order matters - more specific exceptions should be handled before generic ones.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles DuplicateKeyException from MongoDB.
     * Extracts the field name from the error message and returns a user-friendly message.
     * Returns 400 Bad Request.
     * This must be BEFORE the generic Exception handler to take precedence.
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, Object>> handleDuplicateKeyException(DuplicateKeyException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("message", "Bad Request - Validation Failed");

        String errorMessage = ex.getMessage();
        String details = "Duplicate entry detected";

        // Extract field name from MongoDB error message
        // MongoDB error format typically contains field information
        if (errorMessage != null) {
            if (errorMessage.contains("username")) {
                details = "Username already exists in the system";
            } else if (errorMessage.contains("email")) {
                details = "Email already exists in the system";
            } else if (errorMessage.contains("phoneE164")) {
                details = "Phone number already exists in the system";
            } else if (errorMessage.contains("ownerOfClient")) {
                details = "Owner of client already exists in the system";
            } else {
                details = errorMessage;
            }
        }

        response.put("details", details);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles IllegalArgumentException (business logic/validation errors).
     * Returns 400 Bad Request.
     * This must be BEFORE the generic Exception handler to take precedence.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("message", "Bad Request - Validation Failed");
        response.put("details", ex.getMessage());

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles validation errors from @Valid annotation.
     * Returns 400 Bad Request with field-level error details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("message", "Bad Request - Validation Failed");

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );

        // Also add global errors
        ex.getBindingResult().getGlobalErrors().forEach(error ->
            errors.put(error.getObjectName(), error.getDefaultMessage())
        );

        response.put("errors", errors);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles general exceptions.
     * Returns 500 Internal Server Error.
     * This should be the LAST handler to catch anything not handled by specific handlers.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("message", "An unexpected error occurred");
        response.put("details", ex.getMessage());

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
