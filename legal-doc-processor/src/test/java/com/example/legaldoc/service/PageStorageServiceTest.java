package com.example.legaldoc.service;

import com.example.legaldoc.model.DocumentPageManifest;
import com.example.legaldoc.model.PageExtraction;
import com.example.legaldoc.model.PageRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PageStorageServiceTest {

    private S3Service s3Service;
    private ObjectMapper objectMapper;
    private PageStorageService storage;

    @BeforeEach
    void setUp() {
        s3Service = mock(S3Service.class);
        when(s3Service.getBucketName()).thenReturn("test-bucket");
        when(s3Service.uploadBytes(anyString(), any(byte[].class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        storage = new PageStorageService(s3Service, objectMapper);
    }

    @Test
    void buildTmpPrefixSanitizesPathSeparatorsAndWhitespace() {
        String prefix = storage.buildTmpPrefix("doc-42", "My Case File/final draft.pdf");
        assertThat(prefix).isEqualTo("tmp/doc-42/My_Case_File_final_draft.pdf/");
    }

    @Test
    void buildTmpPrefixHandlesMissingFileName() {
        String prefix = storage.buildTmpPrefix("doc-42", null);
        assertThat(prefix).isEqualTo("tmp/doc-42/unnamed/");
    }

    @Test
    void storePageUploadsZeroPaddedTextFileAndReturnsPageRef() {
        PageExtraction page = PageExtraction.builder()
                .pageNumber(3)
                .text("page three text body")
                .images(List.of())
                .build();

        PageRef ref = storage.storePage("tmp/doc-42/caseA.pdf/", page);

        assertThat(ref.getPageNumber()).isEqualTo(3);
        assertThat(ref.getTextS3Key()).isEqualTo("tmp/doc-42/caseA.pdf/pages/page-0003.txt");
        assertThat(ref.getCharCount()).isEqualTo("page three text body".length());
        assertThat(ref.getImageS3Keys()).isEmpty();

        verify(s3Service).uploadBytes(
                eq("tmp/doc-42/caseA.pdf/pages/page-0003.txt"),
                any(byte[].class),
                eq("text/plain; charset=utf-8"));
    }

    @Test
    void storePageAlsoUploadsEachImageWithExpectedKey() {
        PageExtraction page = PageExtraction.builder()
                .pageNumber(7)
                .text("")
                .images(List.of(
                        PageExtraction.ExtractedImage.builder()
                                .imageIndex(1).extension("png").data(new byte[]{1, 2, 3}).build(),
                        PageExtraction.ExtractedImage.builder()
                                .imageIndex(2).extension("jpg").data(new byte[]{4, 5, 6}).build()))
                .build();

        PageRef ref = storage.storePage("tmp/doc-99/file.pdf/", page);

        assertThat(ref.getImageS3Keys()).containsExactly(
                "tmp/doc-99/file.pdf/images/page-0007-img-01.png",
                "tmp/doc-99/file.pdf/images/page-0007-img-02.jpg");
        verify(s3Service).uploadBytes(
                eq("tmp/doc-99/file.pdf/images/page-0007-img-01.png"),
                any(byte[].class),
                eq("image/png"));
        verify(s3Service).uploadBytes(
                eq("tmp/doc-99/file.pdf/images/page-0007-img-02.jpg"),
                any(byte[].class),
                eq("image/jpeg"));
    }

    @Test
    void storeManifestWritesJsonToExpectedKey() throws Exception {
        DocumentPageManifest manifest = storage.newManifest(
                "doc-1", "report.pdf", "application/pdf", false, List.of());
        manifest.setCreatedAt(Instant.parse("2025-04-22T14:05:00Z"));

        String key = storage.storeManifest(manifest);

        assertThat(key).isEqualTo("tmp/doc-1/report.pdf/manifest.json");

        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(s3Service).uploadBytes(eq(key), body.capture(), eq("application/json"));

        String json = new String(body.getValue(), StandardCharsets.UTF_8);
        assertThat(json).contains("\"documentId\":\"doc-1\"");
        assertThat(json).contains("\"fileName\":\"report.pdf\"");
        // JavaTimeModule serializes Instant as ISO-8601 string.
        assertThat(json).contains("2025-04-22T14:05:00Z");
    }
}
