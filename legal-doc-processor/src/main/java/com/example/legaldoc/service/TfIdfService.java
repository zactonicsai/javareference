package com.example.legaldoc.service;

import com.example.legaldoc.model.KeywordScore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Simple TF-IDF implementation scoped to a fixed list of legal/crime keywords.
 *
 * Since we do not have a true document corpus we use a "pseudo-IDF" that is
 * pre-computed from assumed rarity weights (shorter, more specific terms like
 * "homicide" or "racketeering" get a higher IDF than common ones like "crime").
 * The score produced is therefore: tf(term, doc) * idf(term).
 */
@Service
@Slf4j
public class TfIdfService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z'-]+");

    private List<String> keywords = new ArrayList<>();
    private Map<String, Double> idfWeights = new HashMap<>();

    @PostConstruct
    public void loadKeywords() throws IOException {
        ClassPathResource resource = new ClassPathResource("keywords/legal-keywords.txt");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            keywords = reader.lines()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                    .map(String::toLowerCase)
                    .distinct()
                    .collect(Collectors.toList());
        }
        log.info("Loaded {} legal keywords", keywords.size());

        // Pseudo-IDF: longer/rarer words get higher weight.
        // Common generic terms get a deliberate down-weight.
        Set<String> commonTerms = Set.of(
                "crime", "evidence", "investigation", "witness", "suspect", "victim");

        for (String kw : keywords) {
            double base = 1.0 + Math.log(1 + kw.length());
            if (commonTerms.contains(kw)) {
                base *= 0.5;
            }
            idfWeights.put(kw, base);
        }
    }

    public List<KeywordScore> computeScores(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        Map<String, Integer> termFrequencies = tokenizeAndCount(text);
        int totalTokens = termFrequencies.values().stream().mapToInt(Integer::intValue).sum();
        if (totalTokens == 0) {
            return Collections.emptyList();
        }

        List<KeywordScore> results = new ArrayList<>();
        for (String keyword : keywords) {
            int freq = termFrequencies.getOrDefault(keyword, 0);
            if (freq > 0) {
                double tf = (double) freq / totalTokens;
                double idf = idfWeights.getOrDefault(keyword, 1.0);
                double score = tf * idf;
                results.add(new KeywordScore(keyword, score, freq));
            }
        }

        results.sort(Comparator.comparingDouble(KeywordScore::getScore).reversed());
        return results;
    }

    public List<KeywordScore> topN(String text, int n) {
        return computeScores(text).stream().limit(n).collect(Collectors.toList());
    }

    private Map<String, Integer> tokenizeAndCount(String text) {
        Map<String, Integer> counts = new HashMap<>();
        var matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            counts.merge(token, 1, Integer::sum);
        }
        return counts;
    }

    public List<String> getKeywords() {
        return Collections.unmodifiableList(keywords);
    }
}
