package com.example.legaldoc.service;

import com.example.legaldoc.model.DocumentPageManifest;
import com.example.legaldoc.model.PageExtraction;
import com.example.legaldoc.model.PageRef;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the S3 tmp layout for per-page artifacts.
 *
 * <pre>
 * tmp/{documentId}/{safeFileName}/
 *     pages/page-0001.txt
 *     pages/page-0002.txt
 *     pages/page-0003.txt
 *     images/page-0001-img-01.png
 *     images/page-0003-img-01.jpg
 *     manifest.json
 * </pre>
 *
 * The page number is zero-padded to 4 digits so a lexicographic S3 list still
 * returns pages in document order. Callers downstream can re-assemble the
 * document from the manifest alone.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PageStorageService {

    private final S3Service s3Service;
    private final ObjectMapper objectMapper;

    public String buildTmpPrefix(String documentId, String fileName) {
        String safeName = sanitize(fileName);
        return "tmp/" + documentId + "/" + safeName + "/";
    }

    /**
     * Persist a single extracted page's text + images under the document's tmp
     * prefix and return a {@link PageRef} suitable for inclusion in the manifest.
     */
    public PageRef storePage(String tmpPrefix, PageExtraction page) {
        String paddedNum = String.format("%04d", page.getPageNumber());

        String textKey = tmpPrefix + "pages/page-" + paddedNum + ".txt";
        byte[] textBytes = (page.getText() == null ? "" : page.getText())
                .getBytes(StandardCharsets.UTF_8);
        s3Service.uploadBytes(textKey, textBytes, "text/plain; charset=utf-8");

        List<String> imageKeys = new ArrayList<>();
        if (page.getImages() != null) {
            for (PageExtraction.ExtractedImage img : page.getImages()) {
                String imgIdx = String.format("%02d", img.getImageIndex());
                String imgKey = tmpPrefix + "images/page-" + paddedNum + "-img-" + imgIdx
                        + "." + (img.getExtension() == null ? "bin" : img.getExtension());
                s3Service.uploadBytes(imgKey, img.getData(), contentTypeFor(img.getExtension()));
                imageKeys.add(imgKey);
            }
        }

        return PageRef.builder()
                .pageNumber(page.getPageNumber())
                .textS3Key(textKey)
                .charCount(page.getText() == null ? 0 : page.getText().length())
                .imageS3Keys(imageKeys)
                .build();
    }

    /**
     * Write the manifest JSON to {@code {tmpPrefix}manifest.json}.
     */
    public String storeManifest(DocumentPageManifest manifest) throws JsonProcessingException {
        String key = manifest.getTmpPrefix() + "manifest.json";
        byte[] json = objectMapper.writeValueAsBytes(manifest);
        s3Service.uploadBytes(key, json, "application/json");
        log.info("Stored manifest at s3://{}/{} ({} pages)",
                s3Service.getBucketName(), key, manifest.getTotalPages());
        return key;
    }

    public DocumentPageManifest newManifest(String documentId,
                                            String fileName,
                                            String sourceContentType,
                                            boolean syntheticPagination,
                                            List<PageRef> pages) {
        String prefix = buildTmpPrefix(documentId, fileName);
        return DocumentPageManifest.builder()
                .documentId(documentId)
                .fileName(fileName)
                .sourceContentType(sourceContentType)
                .tmpPrefix(prefix)
                .totalPages(pages.size())
                .syntheticPagination(syntheticPagination)
                .pages(pages)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Replace path separators and other characters that would create unintended
     * "directories" in S3. Whitespace and slashes become underscores; we keep
     * the original filename otherwise so it stays human-readable in the console.
     */
    static String sanitize(String fileName) {
        if (fileName == null || fileName.isBlank()) return "unnamed";
        return fileName.replaceAll("[\\\\/\\s]+", "_");
    }

    private static String contentTypeFor(String ext) {
        if (ext == null) return "application/octet-stream";
        return switch (ext.toLowerCase()) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "tiff", "tif" -> "image/tiff";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }
}
