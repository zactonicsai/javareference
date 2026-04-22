package com.example.legaldoc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * A single extracted page of a source document, before upload to S3.
 *
 * For PDFs, one instance per page. For DOCX, one instance per "page" after
 * splitting on explicit page breaks (or by paragraph-count fallback). For
 * text files, the whole document is one page; larger text files are rendered
 * through the PDF path first.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageExtraction {
    /** 1-based page number. */
    private int pageNumber;

    /** Extracted UTF-8 text for this page. May be empty if the page is image-only. */
    private String text;

    /** Embedded images found on this page, in document order. */
    @Builder.Default
    private List<ExtractedImage> images = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedImage {
        /** 1-based image index on the page. */
        private int imageIndex;
        /** Lowercase file extension without dot, e.g. "png", "jpg". */
        private String extension;
        /** Raw image bytes. */
        private byte[] data;
    }
}
