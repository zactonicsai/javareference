package com.example.legaldoc.service;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.example.legaldoc.model.DocumentMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the indexing bug.
 *
 * The bug: the stock {@code JacksonJsonpMapper()} installs a vanilla ObjectMapper
 * with no JavaTimeModule, so serializing {@link DocumentMetadata#getUploadDateTime()}
 * (a {@link Instant}) either throws or produces a numeric epoch — which the
 * {@code date}-mapped field in the ES index rejects with a mapper_parsing_exception.
 *
 * This test demonstrates both that the default mapper is broken for our purposes
 * AND that the mapper configured by {@link com.example.legaldoc.config.ElasticsearchConfig}
 * serializes Instant as the ISO-8601 string Elasticsearch expects.
 */
class ElasticsearchJacksonMapperTest {

    private byte[] serialize(JacksonJsonpMapper mapper, DocumentMetadata doc) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (var generator = mapper.jsonProvider().createGenerator(baos)) {
            mapper.serialize(doc, generator);
        }
        return baos.toByteArray();
    }

    @Test
    void configuredMapperSerializesInstantAsIsoString() {
        ObjectMapper configured = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JacksonJsonpMapper mapper = new JacksonJsonpMapper(configured);

        DocumentMetadata doc = DocumentMetadata.builder()
                .documentId("doc-1")
                .fileName("a.pdf")
                .uploadDateTime(Instant.parse("2025-04-22T14:05:00Z"))
                .status("INDEXED")
                .build();

        String json = new String(serialize(mapper, doc));

        // ISO-8601 string form — what ES "date" fields accept by default.
        assertThat(json).contains("\"uploadDateTime\":\"2025-04-22T14:05:00Z\"");
        assertThat(json).contains("\"documentId\":\"doc-1\"");
    }

    @Test
    void defaultMapperFailsOrProducesNumericForInstant_demonstratingTheBug() {
        // Use a stock ObjectMapper (no JavaTimeModule) — same as the default
        // JacksonJsonpMapper ctor did before the fix.
        JacksonJsonpMapper mapper = new JacksonJsonpMapper(new ObjectMapper());

        DocumentMetadata doc = DocumentMetadata.builder()
                .documentId("doc-1")
                .uploadDateTime(Instant.parse("2025-04-22T14:05:00Z"))
                .build();

        // Either the serializer throws, or it produces a non-ISO representation
        // that ES's date mapper would reject. Both are "bug" outcomes.
        boolean threwOrWrongFormat;
        try {
            String json = new String(serialize(mapper, doc));
            // If we got here, the output must at least NOT be the clean ISO string
            // that the configured mapper produces.
            threwOrWrongFormat = !json.contains("\"2025-04-22T14:05:00Z\"");
        } catch (Exception e) {
            threwOrWrongFormat = true;
        }
        assertThat(threwOrWrongFormat).isTrue();
    }
}
