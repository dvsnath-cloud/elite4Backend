package com.elite4.anandan.registrationservices.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Local file system storage implementation
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "file.storage.type", havingValue = "LOCAL", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    @Value("${file.storage.local.path:uploads/}")
    private String localPath;

    public LocalFileStorageService() {
        // Create uploads directory if it doesn't exist
    }

    @Override
    public String uploadFile(String fileName, byte[] fileContent, String contentType) {
        try {
            // Create directory if it doesn't exist
            Path dirPath = Paths.get(localPath);
            Files.createDirectories(dirPath);

            // Create file path
            Path filePath = dirPath.resolve(fileName);

            // Write file
            Files.write(filePath, fileContent);

            log.info("File uploaded successfully: {}", filePath.toString());
            return filePath.toString();
        } catch (IOException e) {
            log.error("Error uploading file: {}", fileName, e);
            throw new RuntimeException("Failed to upload file: " + fileName, e);
        }
    }

    @Override
    public byte[] downloadFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                throw new RuntimeException("File not found: " + filePath);
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            log.error("Error downloading file: {}", filePath, e);
            throw new RuntimeException("Failed to download file: " + filePath, e);
        }
    }

    @Override
    public void deleteFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("File deleted successfully: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Error deleting file: {}", filePath, e);
            throw new RuntimeException("Failed to delete file: " + filePath, e);
        }
    }

    @Override
    public boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }

    @Override
    public String generateFileName(String registrationId, String originalFileName, String fileType) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String extension = getFileExtension(originalFileName);
        return String.format("%s_%s_%s_%s%s", registrationId, fileType, timestamp,
                System.nanoTime(), extension);
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(lastDot);
        }
        return "";
    }
}

