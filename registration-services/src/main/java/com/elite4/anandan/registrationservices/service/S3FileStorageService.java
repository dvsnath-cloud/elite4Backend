package com.elite4.anandan.registrationservices.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
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
 * AWS S3 file storage implementation.
 * Activated when file.storage.type=S3
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "file.storage.type", havingValue = "S3")
public class S3FileStorageService implements FileStorageService {

    @Value("${file.storage.s3.bucket}")
    private String bucketName;

    @Value("${file.storage.s3.region:us-east-1}")
    private String region;

    @Value("${file.storage.s3.access-key:}")
    private String accessKey;

    @Value("${file.storage.s3.secret-key:}")
    private String secretKey;

    @Value("${file.storage.s3.prefix:colive-files/}")
    private String keyPrefix;

    private S3Client s3Client;

    @PostConstruct
    public void init() {
        try {
            var builder = S3Client.builder().region(Region.of(region));
            if (accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty()) {
                AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
                builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
                log.info("S3 client initialized with static credentials for bucket: {}", bucketName);
            } else {
                builder.credentialsProvider(DefaultCredentialsProvider.create());
                log.info("S3 client initialized with default credentials for bucket: {}", bucketName);
            }
            this.s3Client = builder.build();
        } catch (Exception e) {
            log.error("Failed to initialize S3 client: {}", e.getMessage(), e);
            throw new RuntimeException("S3 initialization failed", e);
        }
    }

    @Override
    public String uploadFile(String fileName, byte[] fileContent, String contentType) {
        try {
            String key = keyPrefix + fileName;

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(fileContent));

            String s3Path = String.format("s3://%s/%s", bucketName, key);
            log.info("File uploaded to S3: {}", s3Path);
            return s3Path;
        } catch (Exception e) {
            log.error("Error uploading file to S3: {}", fileName, e);
            throw new RuntimeException("Failed to upload file to S3: " + fileName, e);
        }
    }

    @Override
    public byte[] downloadFile(String filePath) {
        try {
            String key = extractKey(filePath);

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
            String key = extractKey(filePath);

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
            String key = extractKey(filePath);

            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.headObject(request);
            return true;
        } catch (Exception e) {
            log.debug("File not found in S3: {}", filePath);
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
     * Extract S3 object key from a stored path.
     * Handles both "s3://bucket/key" and plain key formats.
     */
    private String extractKey(String filePath) {
        if (filePath == null) {
            throw new IllegalArgumentException("File path cannot be null");
        }
        // Handle s3://bucket/key format
        String s3Prefix = "s3://" + bucketName + "/";
        if (filePath.startsWith(s3Prefix)) {
            return filePath.substring(s3Prefix.length());
        }
        // Handle s3:// with different bucket (shouldn't happen, but be safe)
        if (filePath.startsWith("s3://")) {
            String withoutScheme = filePath.substring(5);
            int slashIndex = withoutScheme.indexOf('/');
            return slashIndex >= 0 ? withoutScheme.substring(slashIndex + 1) : withoutScheme;
        }
        // Already a plain key
        return filePath;
    }
}

