package com.example.legaldoc.controller;

import com.example.legaldoc.model.DocumentMetadata;
import com.example.legaldoc.service.ElasticsearchService;
import com.example.legaldoc.service.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Per-document endpoints used by the UI: fetch metadata, stream the original
 * file back from S3 for download, or return the extracted text as plain text.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Documents", description = "Per-document lookup, download, and extracted-text access.")
public class DocumentController {

    private final ElasticsearchService elasticsearchService;
    private final S3Service s3Service;

    @Operation(summary = "Get a single document's indexed metadata")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DocumentMetadata.class))),
            @ApiResponse(responseCode = "404", description = "No document with that id.")
    })
    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentMetadata> getById(
            @Parameter(description = "Document UUID.", required = true) @PathVariable String documentId) {
        try {
            DocumentMetadata doc = elasticsearchService.getById(documentId);
            if (doc == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(doc);
        } catch (Exception e) {
            log.error("getById failed for {}", documentId, e);
            return ResponseEntity.status(503).build();
        }
    }

    @Operation(
            summary = "Download the original uploaded file",
            description = "Streams the S3 object that was stored at upload time. Content-Type is "
                    + "the stored fileType; the browser Content-Disposition attachment forces a download."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "File stream. Content-Type matches the stored fileType.",
                    content = @Content(mediaType = "application/octet-stream")),
            @ApiResponse(responseCode = "404", description = "Unknown document id.")
    })
    @GetMapping("/{documentId}/download")
    public ResponseEntity<InputStreamResource> download(
            @Parameter(description = "Document UUID.", required = true) @PathVariable String documentId) {
        DocumentMetadata doc;
        try {
            doc = elasticsearchService.getById(documentId);
        } catch (Exception e) {
            log.error("Download lookup failed", e);
            return ResponseEntity.status(503).build();
        }
        if (doc == null || doc.getS3Key() == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            InputStream stream = s3Service.downloadFile(doc.getS3Key());
            MediaType contentType = doc.getFileType() != null
                    ? safeParseMediaType(doc.getFileType())
                    : MediaType.APPLICATION_OCTET_STREAM;

            // RFC 5987 encoded filename handles non-ASCII filenames cleanly.
            String name = doc.getFileName() != null ? doc.getFileName() : "download";
            String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
            String disposition = "attachment; filename=\"" + name.replace("\"", "") + "\"; "
                    + "filename*=UTF-8''" + encoded;

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, disposition);
            if (doc.getFileSize() != null) {
                headers.setContentLength(doc.getFileSize());
            }

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(contentType)
                    .body(new InputStreamResource(stream));
        } catch (Exception e) {
            log.error("Download failed for documentId={}", documentId, e);
            return ResponseEntity.status(503).build();
        }
    }

    @Operation(
            summary = "View the extracted full text of a document",
            description = "Returns the concatenated extracted text as `text/plain; charset=UTF-8`. "
                    + "For very large documents you may prefer to walk the manifest instead."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(mediaType = "text/plain")),
            @ApiResponse(responseCode = "404", description = "Unknown document id.")
    })
    @GetMapping(value = "/{documentId}/text", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> viewText(
            @Parameter(description = "Document UUID.", required = true) @PathVariable String documentId) {
        try {
            DocumentMetadata doc = elasticsearchService.getById(documentId);
            if (doc == null) return ResponseEntity.notFound().build();
            String text = doc.getExtractedText() != null ? doc.getExtractedText() : "";
            return ResponseEntity.ok(text);
        } catch (Exception e) {
            log.error("viewText failed", e);
            return ResponseEntity.status(503).build();
        }
    }

    private static MediaType safeParseMediaType(String raw) {
        try {
            return MediaType.parseMediaType(raw);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
