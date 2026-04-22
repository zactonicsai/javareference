package com.example.legaldoc.service.extract;

import com.example.legaldoc.model.PageExtraction;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentPageExtractorTest {

    private final DocumentPageExtractor extractor = new DocumentPageExtractor(
            new PdfPageExtractor(),
            new DocxPageExtractor(),
            new TextToPdfConverter());

    @Test
    void smallTextBecomesSingleSyntheticPageWithoutPdfConversion() throws Exception {
        byte[] bytes = "tiny legal notice".getBytes(StandardCharsets.UTF_8);

        DocumentPageExtractor.PagePlan plan = extractor.plan(bytes, "text/plain", "note.txt");

        assertThat(plan.getTotalPages()).isEqualTo(1);
        assertThat(plan.isSyntheticPagination()).isTrue();
        assertThat(plan.getEffectiveContentType()).startsWith("text/");
        // Normalized bytes should be the original — no PDF conversion for small text.
        assertThat(plan.getNormalizedBytes()).isSameAs(bytes);
    }

    @Test
    void largeTextIsRenderedToPdfBeforePagination() throws Exception {
        // > 512KB threshold
        StringBuilder sb = new StringBuilder();
        String line = "the quick brown fox jumps over the lazy dog\n";
        while (sb.length() <= DocumentPageExtractor.LARGE_TEXT_THRESHOLD_BYTES + 1000) {
            sb.append(line);
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        DocumentPageExtractor.PagePlan plan = extractor.plan(bytes, "text/plain", "huge.txt");

        assertThat(plan.getTotalPages()).isGreaterThan(1);
        assertThat(plan.isSyntheticPagination()).isTrue();
        assertThat(plan.getEffectiveContentType()).isEqualTo(DocumentPageExtractor.CT_PDF);
        // Normalization happened: normalizedBytes should NOT be the original.
        assertThat(plan.getNormalizedBytes()).isNotSameAs(bytes);
        // Rendered PDF should start with %PDF-
        assertThat(new String(plan.getNormalizedBytes(), 0, 5, StandardCharsets.US_ASCII))
                .startsWith("%PDF-");
    }

    @Test
    void unknownBinaryTypeEmitsSingleEmptyPageSoManifestStillExists() throws Exception {
        byte[] bytes = new byte[]{0x00, 0x01, 0x02, 0x03, (byte) 0xFF};

        DocumentPageExtractor.PagePlan plan = extractor.plan(bytes, "application/x-mystery", "thing.bin");
        assertThat(plan.getTotalPages()).isEqualTo(1);

        PageExtraction page = extractor.fetchPage(plan.getNormalizedBytes(),
                plan.getEffectiveContentType(), 1);
        assertThat(page.getPageNumber()).isEqualTo(1);
    }

    @Test
    void streamingExtractPagesDelegatesThroughPlanAndFetch() throws Exception {
        byte[] bytes = "simple".getBytes(StandardCharsets.UTF_8);

        List<PageExtraction> collected = new ArrayList<>();
        int total = extractor.extractPages(bytes, "text/plain", "a.txt", collected::add);

        assertThat(total).isEqualTo(1);
        assertThat(collected).hasSize(1);
        assertThat(collected.get(0).getText()).isEqualTo("simple");
    }

    @Test
    void pdfDetectionDoesNotRelyOnFilenameAlone() throws Exception {
        // Tiny text with a misleading filename — routing should follow content type, not extension.
        byte[] bytes = "I am text".getBytes(StandardCharsets.UTF_8);

        DocumentPageExtractor.PagePlan plan = extractor.plan(bytes, "text/plain", "wolf-in-sheep.pdf");

        // Because contentType is text/plain and under threshold, this stays text (1 synthetic page).
        assertThat(plan.getTotalPages()).isEqualTo(1);
        assertThat(plan.getEffectiveContentType()).startsWith("text/");
    }
}
