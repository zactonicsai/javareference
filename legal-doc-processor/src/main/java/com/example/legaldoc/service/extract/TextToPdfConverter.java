package com.example.legaldoc.service.extract;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Renders a (potentially very large) plain-text document onto LETTER-sized PDF
 * pages so it can be processed page-by-page by the same pipeline used for
 * native PDFs. Used for any plain-text source above
 * {@link com.example.legaldoc.service.extract.DocumentPageExtractor#LARGE_TEXT_THRESHOLD_BYTES}.
 *
 * Layout is deliberately simple — monospace Courier, hard-wrapped to a fixed
 * character width — because the goal here is to produce a paginated artifact
 * for downstream consumers, not a typographically polished document.
 */
@Component
@Slf4j
public class TextToPdfConverter {

    private static final float MARGIN = 50f;
    private static final float FONT_SIZE = 10f;
    private static final float LEADING = 12f;
    /** Fits well within the printable width of LETTER at Courier 10pt. */
    private static final int MAX_CHARS_PER_LINE = 95;

    public byte[] convert(String text) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.COURIER);
            PDRectangle pageSize = PDRectangle.LETTER;
            float usableHeight = pageSize.getHeight() - 2 * MARGIN;
            int linesPerPage = Math.max(1, (int) (usableHeight / LEADING));

            String[] logicalLines = text == null ? new String[0] : text.split("\r?\n", -1);
            int totalPages = 0;
            int lineIdx = 0;

            while (lineIdx < logicalLines.length) {
                PDPage page = new PDPage(pageSize);
                doc.addPage(page);
                totalPages++;
                try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                    stream.beginText();
                    stream.setFont(font, FONT_SIZE);
                    stream.setLeading(LEADING);
                    stream.newLineAtOffset(MARGIN, pageSize.getHeight() - MARGIN);

                    int linesThisPage = 0;
                    while (lineIdx < logicalLines.length && linesThisPage < linesPerPage) {
                        String line = logicalLines[lineIdx];
                        // Hard-wrap overlong lines so they don't run off the page.
                        // Only emit what fits on this page; any wrapped remainder
                        // spills onto the next page naturally via the outer loop.
                        int offset = 0;
                        while (offset < line.length() && linesThisPage < linesPerPage) {
                            int end = Math.min(offset + MAX_CHARS_PER_LINE, line.length());
                            String chunk = line.substring(offset, end);
                            // PDFBox's PDType1Font can't render characters outside its
                            // WinAnsi encoding; strip them to avoid IllegalArgumentException.
                            chunk = sanitizeForWinAnsi(chunk);
                            stream.showText(chunk);
                            stream.newLine();
                            linesThisPage++;
                            offset = end;
                        }
                        // Advance to next logical line only if we fully consumed the current one.
                        if (offset >= line.length()) {
                            lineIdx++;
                        } else {
                            // Page is full; remember remainder by rewriting the logical line.
                            logicalLines[lineIdx] = line.substring(offset);
                        }
                    }
                    stream.endText();
                }
            }

            if (totalPages == 0) {
                // Always produce at least one page so downstream pagers don't choke.
                doc.addPage(new PDPage(pageSize));
            }

            doc.save(baos);
            log.info("Rendered {}-char text blob into {}-page PDF ({} bytes)",
                    text == null ? 0 : text.length(), doc.getNumberOfPages(), baos.size());
            return baos.toByteArray();
        }
    }

    /**
     * PDType1Font's built-in WinAnsi encoding rejects characters outside its glyph set
     * (e.g. many CJK ranges, a lot of symbol math). Replace those with '?' so the
     * converter never throws on unusual input.
     */
    private static String sanitizeForWinAnsi(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\t') {
                sb.append("    ");
            } else if (c >= 0x20 && c < 0x7F) {
                sb.append(c);
            } else if (c >= 0xA0 && c <= 0xFF) {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }
}
