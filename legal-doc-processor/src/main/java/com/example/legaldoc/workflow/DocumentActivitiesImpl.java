package com.example.legaldoc.workflow;

import com.example.legaldoc.model.DocumentMetadata;
import com.example.legaldoc.model.DocumentPageManifest;
import com.example.legaldoc.model.KeywordScore;
import com.example.legaldoc.model.PageRef;
import com.example.legaldoc.model.SqsDocumentMessage;
import com.example.legaldoc.service.ElasticsearchService;
import com.example.legaldoc.service.PageStorageService;
import com.example.legaldoc.service.S3Service;
import com.example.legaldoc.service.TfIdfService;
import com.example.legaldoc.service.extract.DocumentPageExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentActivitiesImpl implements DocumentActivities {

    private final S3Service s3Service;
    private final PageStorageService pageStorageService;
    private final DocumentPageExtractor documentPageExtractor;
    private final TfIdfService tfIdfService;
    private final ElasticsearchService elasticsearchService;

    @Override
    public PlanResult planDocument(SqsDocumentMessage message) {
        log.info("Activity: planDocument for documentId={}, s3Key={}",
                message.getDocumentId(), message.getS3Key());
        try {
            byte[] sourceBytes = s3Service.downloadFileAsBytes(message.getS3Key());

            DocumentPageExtractor.PagePlan plan = documentPageExtractor.plan(
                    sourceBytes, message.getFileType(), message.getFileName());

            String tmpPrefix = pageStorageService.buildTmpPrefix(
                    message.getDocumentId(), message.getFileName());

            // If the source was normalized (e.g. large text rendered to PDF),
            // upload the normalized bytes so page activities can skip the conversion.
            String extractionSourceKey;
            if (plan.getNormalizedBytes() != sourceBytes) {
                extractionSourceKey = tmpPrefix + "source.pdf";
                s3Service.uploadBytes(extractionSourceKey, plan.getNormalizedBytes(), "application/pdf");
                log.info("Normalized source uploaded to {}", extractionSourceKey);
            } else {
                extractionSourceKey = message.getS3Key();
            }

            return PlanResult.builder()
                    .documentId(message.getDocumentId())
                    .fileName(message.getFileName())
                    .sourceContentType(plan.getEffectiveContentType())
                    .extractionSourceS3Key(extractionSourceKey)
                    .totalPages(plan.getTotalPages())
                    .syntheticPagination(plan.isSyntheticPagination())
                    .tmpPrefix(tmpPrefix)
                    .build();
        } catch (IOException e) {
            log.error("planDocument failed for documentId={}", message.getDocumentId(), e);
            throw new RuntimeException("planDocument failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String finalizeManifest(DocumentPageManifest manifest) {
        log.info("Activity: finalizeManifest for documentId={}, {} pages",
                manifest.getDocumentId(), manifest.getTotalPages());
        try {
            if (manifest.getCreatedAt() == null) {
                manifest.setCreatedAt(Instant.now());
            }
            return pageStorageService.storeManifest(manifest);
        } catch (IOException e) {
            log.error("finalizeManifest failed", e);
            throw new RuntimeException("finalizeManifest failed: " + e.getMessage(), e);
        }
    }

    @Override
    public DocumentMetadata indexDocument(DocumentPageManifest manifest, SqsDocumentMessage message) {
        log.info("Activity: indexDocument for documentId={}", manifest.getDocumentId());
        try {
            // Reassemble the full text by pulling each page's text.txt out of S3.
            // Bounded memory: one page at a time.
            StringBuilder all = new StringBuilder();
            for (PageRef page : manifest.getPages()) {
                byte[] pageText = s3Service.downloadFileAsBytes(page.getTextS3Key());
                all.append(new String(pageText, StandardCharsets.UTF_8));
                if (!all.isEmpty() && all.charAt(all.length() - 1) != '\n') {
                    all.append('\n');
                }
            }
            String fullText = all.toString();

            List<KeywordScore> topKeywords = tfIdfService.topN(fullText, 20);
            Map<String, Double> scoreMap = new HashMap<>();
            for (KeywordScore ks : topKeywords) {
                scoreMap.put(ks.getKeyword(), ks.getScore());
            }

            String manifestKey = manifest.getTmpPrefix() + "manifest.json";

            DocumentMetadata doc = DocumentMetadata.builder()
                    .documentId(manifest.getDocumentId())
                    .fileName(manifest.getFileName())
                    .s3Key(message.getS3Key())
                    .subject(message.getSubject())
                    .fileType(manifest.getSourceContentType() != null
                            ? manifest.getSourceContentType()
                            : message.getFileType())
                    .fileSize(message.getFileSize())
                    .pageCount(manifest.getTotalPages())
                    .uploadDateTime(message.getUploadDateTime() != null
                            ? Instant.parse(message.getUploadDateTime())
                            : Instant.now())
                    .latitude(message.getLatitude())
                    .longitude(message.getLongitude())
                    .locationDescription(message.getLocationDescription())
                    .extractedText(fullText)
                    .topKeywords(topKeywords)
                    .keywordScores(scoreMap)
                    .manifestS3Key(manifestKey)
                    .tmpPrefix(manifest.getTmpPrefix())
                    .status("INDEXED")
                    .build();

            elasticsearchService.indexDocument(doc);
            return doc;
        } catch (IOException e) {
            log.error("indexDocument failed", e);
            throw new RuntimeException("indexDocument failed: " + e.getMessage(), e);
        }
    }
}
