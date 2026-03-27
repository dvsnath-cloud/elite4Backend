package com.elite4.anandan.registrationservices.service;

/**
 * Interface for file storage operations supporting multiple backends (Local, S3, Azure)
 */
public interface FileStorageService {

    /**
     * Upload a file and return the file path/URL
     * @param fileName the name of the file
     * @param fileContent the content of the file as byte array
     * @param contentType the MIME type of the file
     * @return the file path or URL
     */
    String uploadFile(String fileName, byte[] fileContent, String contentType);

    /**
     * Download a file by its path
     * @param filePath the path or URL of the file
     * @return the file content as byte array
     */
    byte[] downloadFile(String filePath);

    /**
     * Delete a file by its path
     * @param filePath the path or URL of the file
     */
    void deleteFile(String filePath);

    /**
     * Check if a file exists
     * @param filePath the path or URL of the file
     * @return true if file exists, false otherwise
     */
    boolean fileExists(String filePath);

    /**
     * Generate a unique file name based on registration ID and file type
     * @param registrationId the registration ID
     * @param originalFileName the original file name
     * @param fileType the type of file (e.g., "aadhar", "document")
     * @return a unique file name
     */
    String generateFileName(String registrationId, String originalFileName, String fileType);
}

