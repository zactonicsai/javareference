package com.example.legaldoc.controller;

import com.example.legaldoc.model.DocumentMetadata;
import com.example.legaldoc.service.ElasticsearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Search", description = "Query indexed document metadata by extracted keyword.")
public class SearchController {

    private final ElasticsearchService elasticsearchService;

    @Operation(
            summary = "Search documents by TF-IDF keyword",
            description = "Exact-term lookup against the nested `topKeywords.keyword` field. "
                    + "The query term is lowercased server-side to match the lowercased storage form."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "List of matching documents (empty if none match).",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = DocumentMetadata.class)))),
            @ApiResponse(responseCode = "503",
                    description = "Elasticsearch is unavailable. Response body is an empty list.",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = DocumentMetadata.class))))
    })
    @GetMapping("/keyword")
    public ResponseEntity<List<DocumentMetadata>> searchByKeyword(
            @Parameter(description = "Keyword to search for (case-insensitive).",
                    required = true, example = "homicide")
            @RequestParam("q") String keyword) {
        try {
            return ResponseEntity.ok(elasticsearchService.searchByKeyword(keyword));
        } catch (Exception e) {
            log.error("Search failed", e);
            return ResponseEntity.status(503).body(Collections.emptyList());
        }
    }
}
