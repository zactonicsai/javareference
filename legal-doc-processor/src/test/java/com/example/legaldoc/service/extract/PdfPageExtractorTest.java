package com.example.legaldoc.service.extract;

import com.example.legaldoc.model.PageExtraction;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfPageExtractorTest {

    private final PdfPageExtractor extractor = new PdfPageExtractor();

    /** Build a N-page text-only PDF with a distinctive string on each page. */
    private byte[] buildPdf(List<String> pageTexts) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String line : pageTexts) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(font, 12);
                    cs.newLineAtOffset(72, 720);
                    cs.showText(line);
                    cs.endText();
                }
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    void extractPagesStreamsEachPageOnceInOrder() throws Exception {
        byte[] pdf = buildPdf(List.of("alpha page", "beta page", "gamma page"));

        List<PageExtraction> seen = new ArrayList<>();
        int total = extractor.extractPages(pdf, seen::add);

        assertThat(total).isEqualTo(3);
        assertThat(seen).hasSize(3);
        assertThat(seen.get(0).getPageNumber()).isEqualTo(1);
        assertThat(seen.get(0).getText()).contains("alpha");
        assertThat(seen.get(1).getText()).contains("beta");
        assertThat(seen.get(2).getText()).contains("gamma");
    }

    @Test
    void extractSinglePageReturnsRequestedPage() throws Exception {
        byte[] pdf = buildPdf(List.of("one", "two", "three"));

        PageExtraction page2 = extractor.extractSinglePage(pdf, 2);

        assertThat(page2.getPageNumber()).isEqualTo(2);
        assertThat(page2.getText()).contains("two");
        assertThat(page2.getText()).doesNotContain("one", "three");
    }

    @Test
    void extractSinglePageRejectsOutOfRange() throws Exception {
        byte[] pdf = buildPdf(List.of("solo"));

        assertThatThrownBy(() -> extractor.extractSinglePage(pdf, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> extractor.extractSinglePage(pdf, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getPageCountMatchesSourceDocument() throws Exception {
        byte[] pdf = buildPdf(List.of("p1", "p2", "p3", "p4", "p5"));
        assertThat(extractor.getPageCount(pdf)).isEqualTo(5);
    }

    @Test
    void extractReturnsEmptyImagesForTextOnlyPdf() throws Exception {
        byte[] pdf = buildPdf(List.of("no pictures here"));

        PageExtraction page = extractor.extractSinglePage(pdf, 1);

        assertThat(page.getImages()).isEmpty();
    }
}
