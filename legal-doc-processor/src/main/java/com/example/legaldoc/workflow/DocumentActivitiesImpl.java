package com.example.legaldoc.workflow;

import com.example.legaldoc.model.DocumentMetadata;
import com.example.legaldoc.model.KeywordScore;
import com.example.legaldoc.model.SqsDocumentMessage;
import com.example.legaldoc.service.ElasticsearchService;
import com.example.legaldoc.service.S3Service;
import com.example.legaldoc.service.TextExtractionService;
import com.example.legaldoc.service.TfIdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentActivitiesImpl implements DocumentActivities {

    private final S3Service s3Service;
    private final TextExtractionService textExtractionService;
    private final TfIdfService tfIdfService;
    private final ElasticsearchService elasticsearchService;

    @Override
    public DocumentMetadata extractTextAndMetadata(SqsDocumentMessage message) {
        log.info("Activity: extractTextAndMetadata for documentId={}", message.getDocumentId());
        try {
            byte[] fileBytes = s3Service.downloadFileAsBytes(message.getS3Key());
            TextExtractionService.ExtractionResult extraction =
                    textExtractionService.extractText(fileBytes, message.getFileName());

            return DocumentMetadata.builder()
                    .documentId(message.getDocumentId())
                    .fileName(message.getFileName())
                    .s3Key(message.getS3Key())
                    .subject(message.getSubject())
                    .fileType(extraction.getContentType() != null
                            ? extraction.getContentType()
                            : message.getFileType())
                    .fileSize(message.getFileSize())
                    .pageCount(extraction.getPageCount())
                    .uploadDateTime(message.getUploadDateTime() != null
                            ? Instant.parse(message.getUploadDateTime())
                            : Instant.now())
                    .latitude(message.getLatitude())
                    .longitude(message.getLongitude())
                    .locationDescription(message.getLocationDescription())
                    .extractedText(extraction.getText())
                    .status("EXTRACTED")
                    .build();
        } catch (IOException e) {
            log.error("Text extraction failed", e);
            throw new RuntimeException("Text extraction failed: " + e.getMessage(), e);
        }
    }

    @Override
    public DocumentMetadata computeKeywords(DocumentMetadata document) {
        log.info("Activity: computeKeywords for documentId={}", document.getDocumentId());
        List<KeywordScore> topKeywords = tfIdfService.topN(document.getExtractedText(), 20);
        Map<String, Double> scoreMap = new HashMap<>();
        for (KeywordScore ks : topKeywords) {
            scoreMap.put(ks.getKeyword(), ks.getScore());
        }
        document.setTopKeywords(topKeywords);
        document.setKeywordScores(scoreMap);
        document.setStatus("KEYWORDS_COMPUTED");
        return document;
    }

    @Override
    public String indexToElasticsearch(DocumentMetadata document) {
        log.info("Activity: indexToElasticsearch for documentId={}", document.getDocumentId());
        try {
            String id = elasticsearchService.indexDocument(document);
            document.setStatus("INDEXED");
            return id;
        } catch (IOException e) {
            log.error("Elasticsearch indexing failed", e);
            throw new RuntimeException("Elasticsearch indexing failed: " + e.getMessage(), e);
        }
    }
}
