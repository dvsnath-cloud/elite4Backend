package com.elite4.anandan.registrationservices.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AWS S3 file storage implementation
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "file.storage.type", havingValue = "S3")
public class S3FileStorageService implements FileStorageService {

    @Value("${file.storage.s3.bucket}")
    private String bucketName;

    @Value("${file.storage.s3.region:us-east-1}")
    private String region;

    @Value("${file.storage.s3.access-key}")
    private String accessKey;

    @Value("${file.storage.s3.secret-key}")
    private String secretKey;

    private S3Client s3Client;

    public S3FileStorageService() {
        initializeS3Client();
    }

    private void initializeS3Client() {
        if (accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            this.s3Client = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .build();
            log.info("S3 client initialized for bucket: {}", bucketName);
        } else {
            log.warn("S3 credentials not configured. S3 storage will not work.");
        }
    }

    @Override
    public String uploadFile(String fileName, byte[] fileContent, String contentType) {
        try {
            if (s3Client == null) {
                throw new RuntimeException("S3 client not initialized. Check AWS credentials.");
            }

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(fileContent));

            String s3Url = String.format("s3://%s/%s", bucketName, fileName);
            log.info("File uploaded to S3: {}", s3Url);
            return s3Url;
        } catch (Exception e) {
            log.error("Error uploading file to S3: {}", fileName, e);
            throw new RuntimeException("Failed to upload file to S3: " + fileName, e);
        }
    }

    @Override
    public byte[] downloadFile(String filePath) {
        try {
            if (s3Client == null) {
                throw new RuntimeException("S3 client not initialized.");
            }

            String key = extractKeyFromPath(filePath);

            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3Client.getObject(request).readAllBytes();
        } catch (Exception e) {
            log.error("Error downloading file from S3: {}", filePath, e);
            throw new RuntimeException("Failed to download file from S3: " + filePath, e);
        }
    }

    @Override
    public void deleteFile(String filePath) {
        try {
            if (s3Client == null) {
                throw new RuntimeException("S3 client not initialized.");
            }

            String key = extractKeyFromPath(filePath);

            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(request);
            log.info("File deleted from S3: {}", filePath);
        } catch (Exception e) {
            log.error("Error deleting file from S3: {}", filePath, e);
            throw new RuntimeException("Failed to delete file from S3: " + filePath, e);
        }
    }

    @Override
    public boolean fileExists(String filePath) {
        try {
            if (s3Client == null) {
                return false;
            }

            String key = extractKeyFromPath(filePath);

            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.headObject(request);
            return true;
        } catch (Exception e) {
            log.debug("File not found in S3 or error occurred: {}", filePath);
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

    private String extractKeyFromPath(String filePath) {
        if (filePath.startsWith("s3://")) {
            return filePath.substring((bucketName + 5).length() + 1);
        }
        return filePath;
    }
}

