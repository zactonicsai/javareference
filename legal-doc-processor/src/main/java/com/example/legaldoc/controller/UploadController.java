package com.example.legaldoc.controller;

import com.example.legaldoc.model.SqsDocumentMessage;
import com.example.legaldoc.service.S3Service;
import com.example.legaldoc.service.SqsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@Tag(name = "Upload", description = "Accept legal documents and queue them for processing.")
public class UploadController {

    private final S3Service s3Service;
    private final SqsService sqsService;

    @Operation(
            summary = "Upload a document for processing",
            description = "Stores the file in S3 under a generated document ID, then enqueues "
                    + "an SQS message that the Temporal poller picks up to start the processing workflow."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "File accepted and queued for processing.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "File is empty or required parameters missing.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "S3 or SQS failure.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @Parameter(description = "The legal document to upload (PDF, DOCX, TXT, …).", required = true)
            @RequestParam("file") MultipartFile file,

            @Parameter(description = "Case subject or short title.", required = true, example = "Case #2025-001")
            @RequestParam("subject") String subject,

            @Parameter(description = "Optional latitude of the case location.")
            @RequestParam(value = "latitude", required = false) Double latitude,

            @Parameter(description = "Optional longitude of the case location.")
            @RequestParam(value = "longitude", required = false) Double longitude,

            @Parameter(description = "Optional free-text location description.")
            @RequestParam(value = "locationDescription", required = false) String locationDescription) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }
        if (subject == null || subject.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Subject is required"));
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

    /** Documentation-only schema for the success payload. */
    @Schema(name = "UploadResponse", description = "Success response from the upload endpoint.")
    public static class UploadResponse {
        @Schema(example = "6b4a7c9c-7f7f-4d7e-b4a0-5b0e2b5c8a9f") public String documentId;
        @Schema(example = "6b4a7c9c-.../caseA.pdf")              public String s3Key;
        @Schema(example = "12345-abc-678")                       public String sqsMessageId;
        @Schema(example = "QUEUED")                              public String status;
        @Schema(example = "Case #2025-001")                      public String subject;
    }

    @Schema(name = "ErrorResponse")
    public static class ErrorResponse {
        @Schema(example = "File is empty") public String error;
    }
}
