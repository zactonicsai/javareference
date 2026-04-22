package com.example.legaldoc.service.extract;

import com.example.legaldoc.model.PageExtraction;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Router that picks the right per-format extractor for a source document
 * and exposes two usage modes:
 *
 * <ol>
 *   <li>Streaming all pages to a {@link Consumer} in document order (cheap in-process use).</li>
 *   <li>A two-phase API — {@link #plan} + {@link #fetchPage} — used by the Temporal
 *       workflow so each fan-out activity materializes exactly one page of
 *       text + images in memory.</li>
 * </ol>
 *
 * Routing:
 * <ul>
 *   <li>application/pdf → {@link PdfPageExtractor}</li>
 *   <li>DOCX            → {@link DocxPageExtractor}</li>
 *   <li>text/* above {@link #LARGE_TEXT_THRESHOLD_BYTES} → rendered to PDF via
 *       {@link TextToPdfConverter} and then treated as PDF.</li>
 *   <li>text/* below the threshold → single synthetic page.</li>
 *   <li>anything else   → single synthetic empty page, so the pipeline still produces a manifest.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentPageExtractor {

    /** Text files above this size (in bytes) are rendered to PDF before page extraction. */
    public static final int LARGE_TEXT_THRESHOLD_BYTES = 512 * 1024;

    public static final String CT_PDF = "application/pdf";
    public static final String CT_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String CT_TEXT = "text/plain";

    private final PdfPageExtractor pdfPageExtractor;
    private final DocxPageExtractor docxPageExtractor;
    private final TextToPdfConverter textToPdfConverter;

    /**
     * Output of the planning phase. Carries the normalized source bytes that
     * downstream {@link #fetchPage} calls will use for single-page extraction.
     */
    @Data
    @Builder
    public static class PagePlan {
        private int totalPages;
        private String effectiveContentType;
        private boolean syntheticPagination;
        /** Post-normalization bytes. For large text this is the rendered PDF; otherwise the input. */
        private byte[] normalizedBytes;
    }

    /**
     * Plan the page extraction: detect format, convert large text to PDF if
     * needed, and compute the total page count without yet extracting any
     * page content.
     */
    public PagePlan plan(byte[] bytes, String contentType, String fileName) throws IOException {
        String ct = contentType == null ? "" : contentType.toLowerCase();

        if (ct.contains(CT_PDF)) {
            int pages = pdfPageExtractor.getPageCount(bytes);
            return PagePlan.builder()
                    .totalPages(pages)
                    .effectiveContentType(CT_PDF)
                    .syntheticPagination(false)
                    .normalizedBytes(bytes)
                    .build();
        }

        if (isDocx(ct, fileName)) {
            // Count pages by fully iterating; the extractor is non-streaming
            // for DOCX anyway since all pictures live in a document-wide pool.
            int[] count = {0};
            docxPageExtractor.extractPages(bytes, p -> count[0]++);
            return PagePlan.builder()
                    .totalPages(Math.max(1, count[0]))
                    .effectiveContentType(CT_DOCX)
                    .syntheticPagination(true)
                    .normalizedBytes(bytes)
                    .build();
        }

        if (ct.startsWith("text/") || looksLikeText(bytes)) {
            if (bytes.length > LARGE_TEXT_THRESHOLD_BYTES) {
                log.info("Text source {} exceeds {} bytes; rendering to PDF before page extraction",
                        fileName, LARGE_TEXT_THRESHOLD_BYTES);
                byte[] pdf = textToPdfConverter.convert(new String(bytes, StandardCharsets.UTF_8));
                int pages = pdfPageExtractor.getPageCount(pdf);
                return PagePlan.builder()
                        .totalPages(pages)
                        .effectiveContentType(CT_PDF)
                        .syntheticPagination(true)
                        .normalizedBytes(pdf)
                        .build();
            }
            return PagePlan.builder()
                    .totalPages(1)
                    .effectiveContentType(ct.isBlank() ? CT_TEXT : ct)
                    .syntheticPagination(true)
                    .normalizedBytes(bytes)
                    .build();
        }

        log.warn("No page extractor registered for contentType={}, fileName={}. Planning single empty page.",
                contentType, fileName);
        return PagePlan.builder()
                .totalPages(1)
                .effectiveContentType(ct.isBlank() ? "application/octet-stream" : ct)
                .syntheticPagination(true)
                .normalizedBytes(bytes)
                .build();
    }

    /**
     * Fetch a single page from the already-normalized source. Called once per
     * page by the fan-out page worker activity.
     */
    public PageExtraction fetchPage(byte[] normalizedBytes,
                                    String effectiveContentType,
                                    int pageNumber) throws IOException {
        String ct = effectiveContentType == null ? "" : effectiveContentType.toLowerCase();

        if (ct.contains(CT_PDF)) {
            return pdfPageExtractor.extractSinglePage(normalizedBytes, pageNumber);
        }
        if (ct.contains(CT_DOCX)) {
            // DOCX doesn't have true random-access pages; iterate and pick.
            List<PageExtraction> all = new ArrayList<>();
            docxPageExtractor.extractPages(normalizedBytes, all::add);
            if (pageNumber < 1 || pageNumber > all.size()) {
                throw new IllegalArgumentException("pageNumber " + pageNumber
                        + " out of range [1.." + all.size() + "]");
            }
            return all.get(pageNumber - 1);
        }
        if (pageNumber == 1) {
            return PageExtraction.builder()
                    .pageNumber(1)
                    .text(new String(normalizedBytes, StandardCharsets.UTF_8))
                    .build();
        }
        throw new IllegalArgumentException("Single-page content has no page " + pageNumber);
    }

    /**
     * Convenience streaming form: invokes {@code consumer} once per page in
     * document order. Memory bounds equal one page at a time for all formats.
     */
    public int extractPages(byte[] bytes,
                            String contentType,
                            String fileName,
                            Consumer<PageExtraction> consumer) throws IOException {
        PagePlan plan = plan(bytes, contentType, fileName);
        for (int i = 1; i <= plan.getTotalPages(); i++) {
            consumer.accept(fetchPage(plan.getNormalizedBytes(), plan.getEffectiveContentType(), i));
        }
        return plan.getTotalPages();
    }

    private static boolean isDocx(String ct, String fileName) {
        if (ct.contains(CT_DOCX)) return true;
        return fileName != null && fileName.toLowerCase().endsWith(".docx");
    }

    /** Heuristic: a byte array is "probably text" if its first 2KB is mostly printable ASCII. */
    private static boolean looksLikeText(byte[] bytes) {
        int sample = Math.min(bytes.length, 2048);
        if (sample == 0) return false;
        int printable = 0;
        for (int i = 0; i < sample; i++) {
            byte b = bytes[i];
            if (b == '\n' || b == '\r' || b == '\t' || (b >= 0x20 && b < 0x7F)) {
                printable++;
            }
        }
        return printable * 10 >= sample * 9; // ≥90% printable
    }
}
