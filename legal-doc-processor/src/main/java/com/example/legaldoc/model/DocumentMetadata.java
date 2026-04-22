package com.example.legaldoc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Indexed metadata + extracted text for a processed legal document.")
public class DocumentMetadata {

    @Schema(description = "Document UUID.", example = "6b4a7c9c-7f7f-4d7e-b4a0-5b0e2b5c8a9f")
    private String documentId;

    @Schema(description = "Original file name at upload time.", example = "caseA.pdf")
    private String fileName;

    @Schema(description = "S3 key of the original uploaded file.",
            example = "6b4a7c9c-.../caseA.pdf")
    private String s3Key;

    @Schema(description = "User-supplied case subject / title.", example = "Case #2025-001")
    private String subject;

    @Schema(description = "MIME type detected by Tika (or from the upload).",
            example = "application/pdf")
    private String fileType;

    @Schema(description = "Size of the source file in bytes.")
    private Long fileSize;

    @Schema(description = "Total number of pages (native for PDF; synthetic for DOCX / large text).")
    private Integer pageCount;

    @Schema(description = "Upload timestamp (UTC).")
    private Instant uploadDateTime;

    @Schema(description = "Optional latitude of the case location.")
    private Double latitude;

    @Schema(description = "Optional longitude of the case location.")
    private Double longitude;

    @Schema(description = "Optional human-readable location description.",
            example = "Atlanta")
    private String locationDescription;

    @Schema(description = "Top-N TF-IDF keyword scores for the document.")
    private List<KeywordScore> topKeywords;

    @Schema(description = "Flat keyword→score map, convenient for faceting in UIs.")
    private Map<String, Double> keywordScores;

    @Schema(description = "Full concatenated extracted text. For very large documents "
            + "downstream consumers should prefer per-page text via the manifest.")
    private String extractedText;

    @Schema(description = "Pipeline status: QUEUED | EXTRACTED | KEYWORDS_COMPUTED | INDEXED",
            example = "INDEXED")
    private String status;

    @Schema(description = "S3 key of the per-document page manifest JSON, or null if not paginated.",
            example = "tmp/6b4a7c9c-.../caseA.pdf/manifest.json")
    private String manifestS3Key;

    @Schema(description = "Root S3 prefix under which per-page text and images are stored.")
    private String tmpPrefix;
}
