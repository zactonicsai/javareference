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
@Tag(name = "Search", description = "Query indexed document metadata — by TF-IDF keyword, by free text, or list everything.")
public class SearchController {

    private final ElasticsearchService elasticsearchService;

    @Operation(
            summary = "Search by extracted TF-IDF keyword (exact term)",
            description = "Nested-term match against `topKeywords.keyword`. The query is lowercased "
                    + "server-side to match the lowercased storage form."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = DocumentMetadata.class)))),
            @ApiResponse(responseCode = "503",
                    description = "Elasticsearch unavailable; response body is an empty list.",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = DocumentMetadata.class))))
    })
    @GetMapping("/keyword")
    public ResponseEntity<List<DocumentMetadata>> searchByKeyword(
            @Parameter(description = "Keyword (case-insensitive).", required = true, example = "homicide")
            @RequestParam("q") String keyword) {
        try {
            return ResponseEntity.ok(elasticsearchService.searchByKeyword(keyword));
        } catch (Exception e) {
            log.error("Keyword search failed", e);
            return ResponseEntity.status(503).body(Collections.emptyList());
        }
    }

    @Operation(
            summary = "Free-text search across document content",
            description = "Multi-match over extractedText, fileName, subject, and locationDescription. "
                    + "This is the search powering the 'Search' box in the UI."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = DocumentMetadata.class)))),
            @ApiResponse(responseCode = "503",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = DocumentMetadata.class))))
    })
    @GetMapping
    public ResponseEntity<List<DocumentMetadata>> fullTextSearch(
            @Parameter(description = "Free-text query.", required = true, example = "gunshot residue")
            @RequestParam("q") String query,
            @Parameter(description = "Maximum results to return.", example = "25")
            @RequestParam(value = "size", defaultValue = "25") int size) {
        try {
            return ResponseEntity.ok(elasticsearchService.fullTextSearch(query, size));
        } catch (Exception e) {
            log.error("Full-text search failed", e);
            return ResponseEntity.status(503).body(Collections.emptyList());
        }
    }

    @Operation(
            summary = "List all indexed documents (newest first)",
            description = "Returns every document in the index, sorted by uploadDateTime descending. "
                    + "Capped at `size` entries (default 100). For large indexes use search+pagination."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = DocumentMetadata.class)))),
            @ApiResponse(responseCode = "503",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = DocumentMetadata.class))))
    })
    @GetMapping("/all")
    public ResponseEntity<List<DocumentMetadata>> listAll(
            @Parameter(description = "Maximum results to return.", example = "100")
            @RequestParam(value = "size", defaultValue = "100") int size) {
        try {
            return ResponseEntity.ok(elasticsearchService.listAll(size));
        } catch (Exception e) {
            log.error("List-all failed", e);
            return ResponseEntity.status(503).body(Collections.emptyList());
        }
    }
}
