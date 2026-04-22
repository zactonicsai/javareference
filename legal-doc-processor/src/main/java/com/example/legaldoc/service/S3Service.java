package com.example.legaldoc.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @PostConstruct
    public void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            log.info("S3 bucket '{}' already exists", bucketName);
        } catch (NoSuchBucketException e) {
            log.info("Creating S3 bucket '{}'", bucketName);
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        } catch (Exception e) {
            log.warn("S3 bucket check failed at startup: {}", e.getMessage());
        }
    }

    public String uploadFile(String key, MultipartFile file) throws IOException {
        log.info("Uploading file to S3: bucket={}, key={}, size={}", bucketName, key, file.getSize());

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        s3Client.putObject(putRequest,
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        return key;
    }

    public InputStream downloadFile(String key) {
        log.info("Downloading file from S3: bucket={}, key={}", bucketName, key);
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        return s3Client.getObject(getRequest);
    }

    /**
     * Upload arbitrary bytes (used by the page-worker to write per-page text,
     * per-page images, and the manifest under the document's tmp prefix).
     */
    public String uploadBytes(String key, byte[] data, String contentType) {
        log.debug("Uploading {} bytes to s3://{}/{} (contentType={})",
                data.length, bucketName, key, contentType);
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType == null ? "application/octet-stream" : contentType)
                .contentLength((long) data.length)
                .build();
        s3Client.putObject(putRequest, RequestBody.fromBytes(data));
        return key;
    }

    public void deleteFile(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
    }

    public byte[] downloadFileAsBytes(String key) throws IOException {
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(
                GetObjectRequest.builder().bucket(bucketName).key(key).build())) {
            return response.readAllBytes();
        }
    }

    public boolean isAvailable() {
        try {
            s3Client.listBuckets();
            return true;
        } catch (Exception e) {
            log.debug("S3 unavailable: {}", e.getMessage());
            return false;
        }
    }

    public String getBucketName() {
        return bucketName;
    }
}
