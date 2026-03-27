package com.elite4.anandan.registrationservices.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Azure Blob Storage file storage implementation
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

    private BlobServiceClient blobServiceClient;
    private BlobContainerClient containerClient;

    public AzureFileStorageService() {
        initializeAzureClient();
    }

    private void initializeAzureClient() {
        if (accountName != null && !accountName.isEmpty() && accountKey != null && !accountKey.isEmpty()) {
            String connectionString = String.format(
                    "DefaultEndpointsProtocol=https;AccountName=%s;AccountKey=%s;EndpointSuffix=core.windows.net",
                    accountName, accountKey
            );

            this.blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();

            this.containerClient = blobServiceClient.getBlobContainerClient(containerName);

            // Create container if it doesn't exist
            if (!containerClient.exists()) {
                containerClient.create();
                log.info("Azure container created: {}", containerName);
            }

            log.info("Azure Blob Storage client initialized for container: {}", containerName);
        } else {
            log.warn("Azure credentials not configured. Azure storage will not work.");
        }
    }

    @Override
    public String uploadFile(String fileName, byte[] fileContent, String contentType) {
        try {
            if (containerClient == null) {
                throw new RuntimeException("Azure Blob Storage client not initialized. Check Azure credentials.");
            }

            BlobClient blobClient = containerClient.getBlobClient(fileName);
            blobClient.upload(new ByteArrayInputStream(fileContent), fileContent.length, true);

            String azureUrl = blobClient.getBlobUrl();
            log.info("File uploaded to Azure Blob Storage: {}", azureUrl);
            return azureUrl;
        } catch (Exception e) {
            log.error("Error uploading file to Azure: {}", fileName, e);
            throw new RuntimeException("Failed to upload file to Azure: " + fileName, e);
        }
    }

    @Override
    public byte[] downloadFile(String filePath) {
        try {
            if (containerClient == null) {
                throw new RuntimeException("Azure Blob Storage client not initialized.");
            }

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
            if (containerClient == null) {
                throw new RuntimeException("Azure Blob Storage client not initialized.");
            }

            String blobName = extractBlobName(filePath);
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            blobClient.delete();

            log.info("File deleted from Azure Blob Storage: {}", filePath);
        } catch (Exception e) {
            log.error("Error deleting file from Azure: {}", filePath, e);
            throw new RuntimeException("Failed to delete file from Azure: " + filePath, e);
        }
    }

    @Override
    public boolean fileExists(String filePath) {
        try {
            if (containerClient == null) {
                return false;
            }

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
        return String.format("registrations/%s/%s/%s_%s_%s%s",
                registrationId.substring(0, 2), registrationId, fileType, timestamp,
                System.nanoTime(), extension);
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(lastDot);
        }
        return "";
    }

    private String extractBlobName(String filePath) {
        // Extract blob name from the full URL or path
        if (filePath.contains("/")) {
            return filePath.substring(filePath.lastIndexOf("/") + 1);
        }
        return filePath;
    }
}

