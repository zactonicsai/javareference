package com.example.legaldoc.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Service
@Slf4j
public class TextExtractionService {

    private final Tika tika = new Tika();

    public ExtractionResult extractText(byte[] fileBytes, String fileName) throws IOException {
        log.info("Extracting text from file: {}, size: {}", fileName, fileBytes.length);

        try (ByteArrayInputStream stream = new ByteArrayInputStream(fileBytes)) {
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            metadata.set(Metadata.RESOURCE_NAME_KEY, fileName);
            ParseContext context = new ParseContext();
            AutoDetectParser parser = new AutoDetectParser();

            parser.parse(stream, handler, metadata, context);

            String text = handler.toString();
            String contentType = metadata.get(Metadata.CONTENT_TYPE);
            String pageCountStr = metadata.get("xmpTPg:NPages");
            Integer pageCount = null;
            if (pageCountStr != null) {
                try {
                    pageCount = Integer.parseInt(pageCountStr);
                } catch (NumberFormatException ignored) {}
            }

            log.info("Extracted {} characters from {}, contentType={}, pages={}",
                    text.length(), fileName, contentType, pageCount);

            return new ExtractionResult(text, contentType, pageCount);
        } catch (TikaException | SAXException e) {
            log.error("Failed to extract text from {}: {}", fileName, e.getMessage());
            throw new IOException("Text extraction failed", e);
        }
    }

    public String detectContentType(byte[] fileBytes, String fileName) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(fileBytes)) {
            return tika.detect(stream, fileName);
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractionResult {
        private String text;
        private String contentType;
        private Integer pageCount;
    }
}
