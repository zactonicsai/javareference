package com.example.legaldoc.controller;

import com.example.legaldoc.model.SqsDocumentMessage;
import com.example.legaldoc.service.S3Service;
import com.example.legaldoc.service.SqsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
public class UploadController {

    private final S3Service s3Service;
    private final SqsService sqsService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("subject") String subject,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam(value = "locationDescription", required = false) String locationDescription) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File is empty"));
        }
        if (subject == null || subject.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Subject is required"));
        }

        String documentId = UUID.randomUUID().toString();
        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename()
                : "unnamed";
        String s3Key = documentId + "/" + originalName;

        try {
            s3Service.uploadFile(s3Key, file);

            SqsDocumentMessage msg = SqsDocumentMessage.builder()
                    .documentId(documentId)
                    .s3Key(s3Key)
                    .fileName(originalName)
                    .subject(subject)
                    .latitude(latitude)
                    .longitude(longitude)
                    .locationDescription(locationDescription)
                    .fileType(file.getContentType())
                    .fileSize(file.getSize())
                    .uploadDateTime(Instant.now().toString())
                    .build();

            String messageId = sqsService.sendMessage(msg);

            Map<String, Object> response = new HashMap<>();
            response.put("documentId", documentId);
            response.put("s3Key", s3Key);
            response.put("sqsMessageId", messageId);
            response.put("status", "QUEUED");
            response.put("subject", subject);

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (Exception e) {
            log.error("Upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
