package com.example.legaldoc.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.host}")
    private String host;

    @Value("${elasticsearch.port}")
    private int port;

    /**
     * Build the Elasticsearch client with a Jackson mapper that knows how to
     * serialize JSR-310 types (Instant, LocalDate, etc.) as ISO-8601 strings.
     *
     * <p>This is the fix for the indexing bug: the default {@link JacksonJsonpMapper}
     * constructor installs a vanilla ObjectMapper with no JavaTimeModule, so
     * {@link com.example.legaldoc.model.DocumentMetadata#getUploadDateTime() uploadDateTime}
     * (an {@link java.time.Instant}) would serialize as a numeric epoch — which
     * the {@code date}-typed field in our index mapping rejects with a
     * {@code mapper_parsing_exception}. Registering JavaTimeModule and disabling
     * {@code WRITE_DATES_AS_TIMESTAMPS} produces {@code "2025-04-22T14:05:00.123Z"}
     * instead, which is what the ES {@code date} type expects by default.</p>
     */
    @Bean
    public ElasticsearchClient elasticsearchClient() {
        RestClient restClient = RestClient
                .builder(new HttpHost(host, port, "http"))
                .build();

        ObjectMapper esMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper(esMapper));

        return new ElasticsearchClient(transport);
    }
}
