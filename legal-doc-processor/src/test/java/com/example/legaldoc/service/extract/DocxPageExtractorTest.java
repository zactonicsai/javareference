package com.example.legaldoc.service.extract;

import com.example.legaldoc.model.PageExtraction;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocxPageExtractorTest {

    private final DocxPageExtractor extractor = new DocxPageExtractor();

    @Test
    void splitsOnExplicitPageBreaks() throws IOException {
        byte[] docx = buildDocxWithExplicitBreaks();

        List<PageExtraction> pages = new ArrayList<>();
        int total = extractor.extractPages(docx, pages::add);

        // Three paragraphs: "First", break, "Second", break, "Third"
        // Groups: [First+break], [Second+break], [Third]  =>  3 pages
        assertThat(total).isEqualTo(3);
        assertThat(pages).hasSize(3);
        assertThat(pages.get(0).getText()).contains("First");
        assertThat(pages.get(1).getText()).contains("Second");
        assertThat(pages.get(2).getText()).contains("Third");
        // Ensure content didn't bleed between pages.
        assertThat(pages.get(0).getText()).doesNotContain("Second", "Third");
        assertThat(pages.get(2).getText()).doesNotContain("First", "Second");
    }

    @Test
    void fallsBackToParagraphChunkingWhenNoExplicitBreaks() throws IOException {
        // PARAGRAPHS_PER_PAGE = 40 -> 85 paragraphs should yield 3 pages (40 + 40 + 5).
        byte[] docx = buildDocxWithParagraphs(85);

        List<PageExtraction> pages = new ArrayList<>();
        int total = extractor.extractPages(docx, pages::add);

        assertThat(total).isEqualTo(3);
        assertThat(pages.get(0).getPageNumber()).isEqualTo(1);
        assertThat(pages.get(2).getPageNumber()).isEqualTo(3);
    }

    @Test
    void alwaysEmitsAtLeastOnePageForEmptyDocument() throws IOException {
        byte[] docx = buildDocxWithParagraphs(0);

        List<PageExtraction> pages = new ArrayList<>();
        int total = extractor.extractPages(docx, pages::add);

        assertThat(total).isEqualTo(1);
        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).getPageNumber()).isEqualTo(1);
    }

    private byte[] buildDocxWithExplicitBreaks() throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            writeParagraphWithTrailingPageBreak(doc, "First");
            writeParagraphWithTrailingPageBreak(doc, "Second");
            writeParagraph(doc, "Third");
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] buildDocxWithParagraphs(int count) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (int i = 0; i < count; i++) {
                writeParagraph(doc, "Paragraph " + i);
            }
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    private void writeParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.createRun().setText(text);
    }

    private void writeParagraphWithTrailingPageBreak(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(text);
        // BreakType.PAGE emits <w:br w:type="page"/> which is what the extractor looks for.
        r.addBreak(BreakType.PAGE);
    }
}
