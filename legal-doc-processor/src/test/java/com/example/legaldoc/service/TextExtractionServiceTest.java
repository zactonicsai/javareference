package com.example.legaldoc.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TextExtractionServiceTest {

    private final TextExtractionService service = new TextExtractionService();

    @Test
    void extractsPlainTextFile() throws Exception {
        String content = "This is a legal case involving homicide and fraud.";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        TextExtractionService.ExtractionResult result = service.extractText(bytes, "case.txt");

        assertThat(result.getText()).contains("homicide", "fraud");
        assertThat(result.getContentType()).startsWith("text/plain");
    }

    @Test
    void detectsContentType() {
        byte[] txt = "Hello world".getBytes(StandardCharsets.UTF_8);
        String detected = service.detectContentType(txt, "test.txt");
        assertThat(detected).startsWith("text/");
    }
}
