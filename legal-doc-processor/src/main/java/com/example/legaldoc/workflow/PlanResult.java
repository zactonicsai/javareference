package com.example.legaldoc.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Output of {@link DocumentActivities#planDocument}: tells the workflow how
 * many pages to fan out over, what content type the document resolved to,
 * and where the per-page work should be written in S3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanResult {
    private String documentId;
    private String fileName;
    private String sourceContentType;
    /** If the source was a large text file rendered to PDF, this is the rendered-PDF S3 key. Else equals the original s3Key. */
    private String extractionSourceS3Key;
    private int totalPages;
    private boolean syntheticPagination;
    private String tmpPrefix;
}
