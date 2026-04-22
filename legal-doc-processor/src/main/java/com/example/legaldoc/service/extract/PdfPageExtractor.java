package com.example.legaldoc.service.extract;

import com.example.legaldoc.model.PageExtraction;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Streams pages out of a PDF one at a time using PDFBox. Does NOT load the
 * entire extracted corpus into memory at once — the caller supplies a
 * {@link Consumer} that is invoked per page, which lets the page worker
 * upload to S3 and drop the page bytes immediately.
 *
 * Embedded raster images on each page are surfaced via {@link PageExtraction.ExtractedImage}.
 * Form XObjects (reusable page fragments) are recursed into so images nested inside
 * them are not missed, but a visited-set guards against circular references.
 */
@Component
@Slf4j
public class PdfPageExtractor {

    /**
     * Extract each page of {@code pdfBytes} in order and hand it to {@code consumer}.
     * The consumer should process and discard each {@link PageExtraction} immediately
     * to bound memory use for large PDFs.
     *
     * @return the total number of pages processed
     */
    public int extractPages(byte[] pdfBytes, Consumer<PageExtraction> consumer) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int pageCount = doc.getNumberOfPages();
            log.info("PDF has {} pages", pageCount);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            for (int i = 1; i <= pageCount; i++) {
                consumer.accept(readPage(doc, stripper, i));
                log.debug("Extracted PDF page {}/{}", i, pageCount);
            }
            return pageCount;
        }
    }

    /**
     * Read a single page by index (1-based) without iterating the rest of the document.
     * Used by the page worker activity so each fan-out task only materializes one page
     * of text + images in memory.
     */
    public PageExtraction extractSinglePage(byte[] pdfBytes, int pageNumber) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int pageCount = doc.getNumberOfPages();
            if (pageNumber < 1 || pageNumber > pageCount) {
                throw new IllegalArgumentException(
                        "pageNumber " + pageNumber + " out of range [1.." + pageCount + "]");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return readPage(doc, stripper, pageNumber);
        }
    }

    public int getPageCount(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            return doc.getNumberOfPages();
        }
    }

    private PageExtraction readPage(PDDocument doc, PDFTextStripper stripper, int pageNumber) throws IOException {
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        String text = stripper.getText(doc);
        PDPage page = doc.getPage(pageNumber - 1);
        List<PageExtraction.ExtractedImage> images = extractImages(page);
        return PageExtraction.builder()
                .pageNumber(pageNumber)
                .text(text)
                .images(images)
                .build();
    }

    private List<PageExtraction.ExtractedImage> extractImages(PDPage page) {
        List<PageExtraction.ExtractedImage> out = new ArrayList<>();
        PDResources resources = page.getResources();
        if (resources == null) return out;

        int[] counter = {0};
        Set<COSName> visited = new HashSet<>();
        walkXObjects(resources, visited, out, counter);
        return out;
    }

    private void walkXObjects(PDResources resources,
                              Set<COSName> visited,
                              List<PageExtraction.ExtractedImage> out,
                              int[] counter) {
        for (COSName name : resources.getXObjectNames()) {
            if (!visited.add(name)) continue;
            try {
                PDXObject xobject = resources.getXObject(name);
                if (xobject instanceof PDImageXObject img) {
                    try {
                        BufferedImage bi = img.getImage();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        // Normalize everything to PNG so downstream consumers don't
                        // have to care about CMYK/JBIG2/etc. quirks of the embedded form.
                        ImageIO.write(bi, "png", baos);
                        counter[0]++;
                        out.add(PageExtraction.ExtractedImage.builder()
                                .imageIndex(counter[0])
                                .extension("png")
                                .data(baos.toByteArray())
                                .build());
                    } catch (IOException e) {
                        log.warn("Failed to decode embedded image '{}': {}", name.getName(), e.getMessage());
                    }
                } else if (xobject instanceof PDFormXObject form) {
                    PDResources nested = form.getResources();
                    if (nested != null) {
                        walkXObjects(nested, visited, out, counter);
                    }
                }
            } catch (IOException e) {
                log.warn("Failed to read XObject '{}': {}", name.getName(), e.getMessage());
            }
        }
    }
}
