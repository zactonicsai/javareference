package com.example.legaldoc.controller;

import com.example.legaldoc.model.DocumentMetadata;
import com.example.legaldoc.service.ElasticsearchService;
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
public class SearchController {

    private final ElasticsearchService elasticsearchService;

    @GetMapping("/keyword")
    public ResponseEntity<List<DocumentMetadata>> searchByKeyword(
            @RequestParam("q") String keyword) {
        try {
            return ResponseEntity.ok(elasticsearchService.searchByKeyword(keyword));
        } catch (Exception e) {
            log.error("Search failed", e);
            return ResponseEntity.status(503).body(Collections.emptyList());
        }
    }
}
