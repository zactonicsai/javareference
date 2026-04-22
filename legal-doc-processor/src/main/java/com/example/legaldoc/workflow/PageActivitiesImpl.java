package com.example.legaldoc.workflow;

import com.example.legaldoc.model.PageExtraction;
import com.example.legaldoc.model.PageRef;
import com.example.legaldoc.service.PageStorageService;
import com.example.legaldoc.service.S3Service;
import com.example.legaldoc.service.extract.DocumentPageExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Implementation of the per-page activity. Registered on the
 * {@code legal-doc-page-queue} worker only — see
 * {@link com.example.legaldoc.config.TemporalConfig}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PageActivitiesImpl implements PageActivities {

    private final S3Service s3Service;
    private final DocumentPageExtractor documentPageExtractor;
    private final PageStorageService pageStorageService;

    @Override
    public PageRef extractAndStorePage(PlanResult plan, int pageNumber) {
        log.info("Activity: extractAndStorePage documentId={} page={}/{}",
                plan.getDocumentId(), pageNumber, plan.getTotalPages());
        try {
            byte[] sourceBytes = s3Service.downloadFileAsBytes(plan.getExtractionSourceS3Key());
            PageExtraction page = documentPageExtractor.fetchPage(
                    sourceBytes, plan.getSourceContentType(), pageNumber);
            return pageStorageService.storePage(plan.getTmpPrefix(), page);
        } catch (IOException e) {
            log.error("Page extraction failed for documentId={} page={}",
                    plan.getDocumentId(), pageNumber, e);
            throw new RuntimeException("extractAndStorePage failed: " + e.getMessage(), e);
        }
    }
}
