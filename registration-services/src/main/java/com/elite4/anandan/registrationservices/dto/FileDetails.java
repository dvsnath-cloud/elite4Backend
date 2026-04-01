package com.elite4.anandan.registrationservices.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for file details returned from file storage
 * Contains file metadata and content information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileDetails {

    private String fileName;              // Name of the file
    private String filePath;              // Full path to the file
    private Long fileSize;                // Size of the file in bytes
    private String fileType;              // MIME type (e.g., image/jpeg, application/pdf)
    private byte[] fileContent;           // Binary content of the file (may be null for metadata-only requests)
    private String fileExtension;         // File extension (e.g., jpg, pdf)
    private String contentEncoding;       // Content encoding if applicable
    private boolean exists;               // Whether file exists in storage
    private Instant createdAt;            // File creation timestamp
    private String description;           // File description or purpose
}
