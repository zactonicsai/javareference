package com.example.legaldoc.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Locator for a single page that was stored in the S3 tmp area. Used by the
 * manifest and by downstream consumers that want to re-assemble the document
 * (e.g. stream page-N text back without re-parsing the whole source).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Reference to a single extracted page stored in the S3 tmp area.")
public class PageRef {

    @Schema(description = "1-based page number.", example = "3")
    private int pageNumber;

    @Schema(description = "S3 key for this page's UTF-8 text file.",
            example = "tmp/abc-123/caseA.pdf/pages/page-0003.txt")
    private String textS3Key;

    @Schema(description = "Number of characters in the extracted page text.", example = "2048")
    private int charCount;

    @Schema(description = "S3 keys of images extracted from this page, in document order.")
    @Builder.Default
    private List<String> imageS3Keys = new ArrayList<>();
}
