package com.example.legaldoc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqsDocumentMessage {
    private String documentId;
    private String s3Key;
    private String fileName;
    private String subject;
    private Double latitude;
    private Double longitude;
    private String locationDescription;
    private String fileType;
    private Long fileSize;
    private String uploadDateTime;
}
