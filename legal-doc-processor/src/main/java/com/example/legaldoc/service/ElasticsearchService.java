package com.example.legaldoc.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.example.legaldoc.model.DocumentMetadata;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchService {

    private final ElasticsearchClient esClient;

    @Value("${elasticsearch.indexName}")
    private String indexName;

    @PostConstruct
    public void ensureIndex() {
        try {
            boolean exists = esClient.indices()
                    .exists(ExistsRequest.of(e -> e.index(indexName)))
                    .value();
            if (!exists) {
                esClient.indices().create(CreateIndexRequest.of(c -> c
                        .index(indexName)
                        .mappings(m -> m
                                .properties("documentId", p -> p.keyword(k -> k))
                                .properties("fileName", p -> p.text(t -> t))
                                .properties("s3Key", p -> p.keyword(k -> k))
                                .properties("subject", p -> p.text(t -> t))
                                .properties("fileType", p -> p.keyword(k -> k))
                                .properties("fileSize", p -> p.long_(l -> l))
                                .properties("pageCount", p -> p.integer(i -> i))
                                .properties("uploadDateTime", p -> p.date(d -> d))
                                .properties("latitude", p -> p.double_(d -> d))
                                .properties("longitude", p -> p.double_(d -> d))
                                .properties("locationDescription", p -> p.text(t -> t))
                                .properties("extractedText", p -> p.text(t -> t))
                                .properties("status", p -> p.keyword(k -> k))
                                .properties("topKeywords", p -> p.nested(n -> n
                                        .properties("keyword", pp -> pp.keyword(k -> k))
                                        .properties("score", pp -> pp.double_(d -> d))
                                        .properties("frequency", pp -> pp.integer(i -> i))))
                        )));
                log.info("Created Elasticsearch index '{}'", indexName);
            } else {
                log.info("Elasticsearch index '{}' already exists", indexName);
            }
        } catch (Exception e) {
            log.warn("Could not ensure Elasticsearch index at startup: {}", e.getMessage());
        }
    }

    public String indexDocument(DocumentMetadata doc) throws IOException {
        IndexResponse response = esClient.index(i -> i
                .index(indexName)
                .id(doc.getDocumentId())
                .document(doc));
        log.info("Indexed document {} into ES: result={}", doc.getDocumentId(), response.result());
        return response.id();
    }

    public List<DocumentMetadata> searchByKeyword(String keyword) throws IOException {
        SearchResponse<DocumentMetadata> response = esClient.search(s -> s
                        .index(indexName)
                        .query(Query.of(q -> q.nested(n -> n
                                .path("topKeywords")
                                .query(nq -> nq.term(t -> t
                                        .field("topKeywords.keyword")
                                        .value(keyword.toLowerCase())))))),
                DocumentMetadata.class);

        return response.hits().hits().stream()
                .map(co.elastic.clients.elasticsearch.core.search.Hit::source)
                .collect(Collectors.toList());
    }

    public boolean isAvailable() {
        try {
            HealthResponse health = esClient.cluster().health();
            return health.status() != null;
        } catch (Exception e) {
            log.debug("Elasticsearch unavailable: {}", e.getMessage());
            return false;
        }
    }

    public String getIndexName() {
        return indexName;
    }
}
