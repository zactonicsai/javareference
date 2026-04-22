package com.example.legaldoc.service.extract;

import com.example.legaldoc.model.PageExtraction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextToPdfConverterTest {

    private final TextToPdfConverter converter = new TextToPdfConverter();
    private final PdfPageExtractor pdfExtractor = new PdfPageExtractor();

    @Test
    void convertsEmptyTextToSinglePagePdf() throws Exception {
        byte[] pdf = converter.convert("");
        assertThat(pdfExtractor.getPageCount(pdf)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void convertsShortTextToPdfThatPdfExtractorCanReadBack() throws Exception {
        byte[] pdf = converter.convert("hello from test\nsecond line here");
        PageExtraction page = pdfExtractor.extractSinglePage(pdf, 1);
        assertThat(page.getText()).contains("hello from test");
        assertThat(page.getText()).contains("second line here");
    }

    @Test
    void convertsLargeTextIntoMultiplePages() throws Exception {
        // Produce something comfortably above any reasonable per-page line budget.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.append("line ").append(i).append('\n');
        }
        byte[] pdf = converter.convert(sb.toString());

        int pageCount = pdfExtractor.getPageCount(pdf);
        assertThat(pageCount).isGreaterThan(1);

        // First page should contain early lines; last page should contain late lines.
        PageExtraction first = pdfExtractor.extractSinglePage(pdf, 1);
        PageExtraction last = pdfExtractor.extractSinglePage(pdf, pageCount);
        assertThat(first.getText()).contains("line 0");
        assertThat(last.getText()).contains("line 499");
    }

    @Test
    void nonWinAnsiCharactersAreReplacedNotCrashed() throws Exception {
        // CJK ideographs are outside WinAnsi; converter should replace with '?' and not throw.
        byte[] pdf = converter.convert("safe ASCII then \u4E2D\u6587 then done");

        List<PageExtraction> pages = new ArrayList<>();
        pdfExtractor.extractPages(pdf, pages::add);
        assertThat(pages).isNotEmpty();
        assertThat(pages.get(0).getText()).contains("safe ASCII then");
        assertThat(pages.get(0).getText()).contains("then done");
    }
}
