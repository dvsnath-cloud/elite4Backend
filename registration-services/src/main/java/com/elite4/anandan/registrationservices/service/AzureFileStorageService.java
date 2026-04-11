package com.elite4.anandan.registrationservices.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Azure Blob Storage file storage implementation.
 * Activated when file.storage.type=AZURE
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "file.storage.type", havingValue = "AZURE")
public class AzureFileStorageService implements FileStorageService {

    @Value("${file.storage.azure.container}")
    private String containerName;

    @Value("${file.storage.azure.account-name}")
    private String accountName;

    @Value("${file.storage.azure.account-key}")
    private String accountKey;

    @Value("${file.storage.azure.prefix:colive-files/}")
    private String blobPrefix;

    private BlobContainerClient containerClient;

    @PostConstruct
    public void init() {
        try {
            if (accountName == null || accountName.isEmpty() || accountKey == null || accountKey.isEmpty()) {
                throw new RuntimeException("Azure Storage account name and key must be configured");
            }

            String connectionString = String.format(
                    "DefaultEndpointsProtocol=https;AccountName=%s;AccountKey=%s;EndpointSuffix=core.windows.net",
                    accountName, accountKey
            );

            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();

            this.containerClient = blobServiceClient.getBlobContainerClient(containerName);

            if (!containerClient.exists()) {
                containerClient.create();
                log.info("Azure container created: {}", containerName);
            }

            log.info("Azure Blob Storage client initialized for container: {}", containerName);
        } catch (Exception e) {
            log.error("Failed to initialize Azure Blob Storage client: {}", e.getMessage(), e);
            throw new RuntimeException("Azure Blob Storage initialization failed", e);
        }
    }

    @Override
    public String uploadFile(String fileName, byte[] fileContent, String contentType) {
        try {
            String blobName = blobPrefix + fileName;

            BlobClient blobClient = containerClient.getBlobClient(blobName);
            blobClient.upload(new ByteArrayInputStream(fileContent), fileContent.length, true);

            String azurePath = String.format("azure://%s/%s", containerName, blobName);
            log.info("File uploaded to Azure Blob Storage: {}", azurePath);
            return azurePath;
        } catch (Exception e) {
            log.error("Error uploading file to Azure: {}", fileName, e);
            throw new RuntimeException("Failed to upload file to Azure: " + fileName, e);
        }
    }

    @Override
    public byte[] downloadFile(String filePath) {
        try {
            String blobName = extractBlobName(filePath);
            BlobClient blobClient = containerClient.getBlobClient(blobName);

            return blobClient.downloadContent().toBytes();
        } catch (Exception e) {
            log.error("Error downloading file from Azure: {}", filePath, e);
            throw new RuntimeException("Failed to download file from Azure: " + filePath, e);
        }
    }

    @Override
    public void deleteFile(String filePath) {
        try {
            String blobName = extractBlobName(filePath);
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            blobClient.deleteIfExists();

            log.info("File deleted from Azure Blob Storage: {}", filePath);
        } catch (Exception e) {
            log.error("Error deleting file from Azure: {}", filePath, e);
            throw new RuntimeException("Failed to delete file from Azure: " + filePath, e);
        }
    }

    @Override
    public boolean fileExists(String filePath) {
        try {
            String blobName = extractBlobName(filePath);
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            return blobClient.exists();
        } catch (Exception e) {
            log.debug("File not found in Azure or error occurred: {}", filePath);
            return false;
        }
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

    /**
     * Extract blob name from a stored path.
     * Handles "azure://container/blobName", full HTTPS URLs, and plain blob names.
     */
    private String extractBlobName(String filePath) {
        if (filePath == null) {
            throw new IllegalArgumentException("File path cannot be null");
        }
        // Handle azure://container/blobName format
        String azurePrefix = "azure://" + containerName + "/";
        if (filePath.startsWith(azurePrefix)) {
            return filePath.substring(azurePrefix.length());
        }
        // Handle full HTTPS blob URL
        if (filePath.startsWith("https://")) {
            String containerPath = "/" + containerName + "/";
            int idx = filePath.indexOf(containerPath);
            if (idx >= 0) {
                return filePath.substring(idx + containerPath.length());
            }
        }
        // Already a plain blob name
        return filePath;
    }
}

