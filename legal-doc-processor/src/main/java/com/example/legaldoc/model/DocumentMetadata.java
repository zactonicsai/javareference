package com.example.legaldoc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class DocumentMetadata {
    private String documentId;
    private String fileName;
    private String s3Key;
    private String subject;
    private String fileType;
    private Long fileSize;
    private Integer pageCount;
    private Instant uploadDateTime;
    private Double latitude;
    private Double longitude;
    private String locationDescription;
    private List<KeywordScore> topKeywords;
    private Map<String, Double> keywordScores;
    private String extractedText;
    private String status;
}
