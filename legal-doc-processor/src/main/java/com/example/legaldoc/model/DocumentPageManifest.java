package com.example.legaldoc.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Manifest describing how a source document was split into pages in S3.
 *
 * The canonical copy is written to {@code tmp/{documentId}/{fileName}/manifest.json}.
 * A downstream consumer can list pages, fetch each page's text by its {@code textS3Key},
 * and reassemble the full document without re-running the extraction pipeline.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Manifest of per-page text/image artifacts stored in the S3 tmp area for a document.")
public class DocumentPageManifest {

    @Schema(description = "Document UUID.", example = "6b4a7c9c-7f7f-4d7e-b4a0-5b0e2b5c8a9f")
    private String documentId;

    @Schema(description = "Original file name at upload time.", example = "caseA.pdf")
    private String fileName;

    @Schema(description = "MIME type detected for the source document.", example = "application/pdf")
    private String sourceContentType;

    @Schema(description = "Root prefix in the S3 tmp area for this document.",
            example = "tmp/6b4a7c9c-.../caseA.pdf/")
    private String tmpPrefix;

    @Schema(description = "Total number of pages produced.")
    private int totalPages;

    @Schema(description = "True if the source was not natively paginated (e.g. large text rendered to PDF, DOCX split heuristically).")
    private boolean syntheticPagination;

    @Schema(description = "Ordered list of per-page references.")
    @Builder.Default
    private List<PageRef> pages = new ArrayList<>();

    @Schema(description = "When the manifest was written.")
    private Instant createdAt;
}
