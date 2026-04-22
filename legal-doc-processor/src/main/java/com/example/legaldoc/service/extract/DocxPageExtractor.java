package com.example.legaldoc.service.extract;

import com.example.legaldoc.model.PageExtraction;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBr;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Splits a DOCX into pseudo-pages and streams them one at a time.
 *
 * DOCX has no true "pages" until Word/LibreOffice renders it, so we approximate:
 * <ol>
 *   <li><b>Primary:</b> split on explicit page breaks ({@code <w:br w:type="page"/>})
 *       authored in the document.</li>
 *   <li><b>Fallback:</b> if the document contains no explicit page breaks, chunk the
 *       paragraphs into groups of {@link #PARAGRAPHS_PER_PAGE}.</li>
 * </ol>
 *
 * Embedded pictures (XWPFPictureData) live in a document-wide pool and are not
 * intrinsically associated with a paragraph position, so we attach all images
 * to page 1. This is imperfect but predictable — callers who need page-accurate
 * image placement should convert DOCX→PDF upstream.
 */
@Component
@Slf4j
public class DocxPageExtractor {

    static final int PARAGRAPHS_PER_PAGE = 40;

    /**
     * @return total number of pages produced
     */
    public int extractPages(byte[] docxBytes, Consumer<PageExtraction> consumer) throws IOException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(docxBytes);
             XWPFDocument doc = new XWPFDocument(in)) {

            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            List<List<XWPFParagraph>> pageGroups = groupIntoPages(paragraphs);
            int pageCount = pageGroups.size();

            List<PageExtraction.ExtractedImage> allImages = extractImages(doc);
            log.info("DOCX split into {} pseudo-page(s); {} embedded image(s)",
                    pageCount, allImages.size());

            for (int i = 0; i < pageCount; i++) {
                int pageNumber = i + 1;
                StringBuilder sb = new StringBuilder();
                for (XWPFParagraph p : pageGroups.get(i)) {
                    sb.append(p.getText()).append('\n');
                }

                List<PageExtraction.ExtractedImage> pageImages =
                        (pageNumber == 1) ? allImages : List.of();

                consumer.accept(PageExtraction.builder()
                        .pageNumber(pageNumber)
                        .text(sb.toString())
                        .images(new ArrayList<>(pageImages))
                        .build());
            }
            return pageCount;
        }
    }

    private List<List<XWPFParagraph>> groupIntoPages(List<XWPFParagraph> paragraphs) {
        List<List<XWPFParagraph>> pages = new ArrayList<>();
        List<XWPFParagraph> current = new ArrayList<>();

        boolean sawExplicitBreak = false;
        for (XWPFParagraph p : paragraphs) {
            current.add(p);
            if (containsPageBreak(p)) {
                sawExplicitBreak = true;
                pages.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            pages.add(current);
        }

        // Fallback: no explicit breaks, chunk by paragraph count.
        if (!sawExplicitBreak) {
            pages.clear();
            List<XWPFParagraph> chunk = new ArrayList<>();
            for (XWPFParagraph p : paragraphs) {
                chunk.add(p);
                if (chunk.size() >= PARAGRAPHS_PER_PAGE) {
                    pages.add(chunk);
                    chunk = new ArrayList<>();
                }
            }
            if (!chunk.isEmpty()) pages.add(chunk);
        }

        if (pages.isEmpty()) {
            pages.add(new ArrayList<>());
        }
        return pages;
    }

    private boolean containsPageBreak(XWPFParagraph p) {
        for (XWPFRun run : p.getRuns()) {
            for (CTBr br : run.getCTR().getBrList()) {
                if (br.getType() != null && br.getType() == STBrType.PAGE) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<PageExtraction.ExtractedImage> extractImages(XWPFDocument doc) {
        List<PageExtraction.ExtractedImage> images = new ArrayList<>();
        int idx = 0;
        for (XWPFPictureData pic : doc.getAllPictures()) {
            idx++;
            String ext = pic.suggestFileExtension();
            if (ext == null || ext.isBlank()) ext = "bin";
            images.add(PageExtraction.ExtractedImage.builder()
                    .imageIndex(idx)
                    .extension(ext.toLowerCase())
                    .data(pic.getData())
                    .build());
        }
        return images;
    }
}
