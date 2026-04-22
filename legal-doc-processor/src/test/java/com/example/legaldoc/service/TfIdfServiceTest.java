package com.example.legaldoc.service;

import com.example.legaldoc.model.KeywordScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TfIdfServiceTest {

    private TfIdfService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new TfIdfService();
        service.loadKeywords();
    }

    @Test
    void loadsKeywordList() {
        assertThat(service.getKeywords()).isNotEmpty();
        assertThat(service.getKeywords()).contains("homicide", "fraud", "evidence");
    }

    @Test
    void returnsEmptyForBlankText() {
        assertThat(service.computeScores("")).isEmpty();
        assertThat(service.computeScores(null)).isEmpty();
    }

    @Test
    void identifiesKeywordsInText() {
        String text = """
                The suspect was arrested for homicide after evidence was collected at the crime scene.
                The witness gave testimony about the murder weapon. Forensic analysis confirmed
                the fingerprint on the firearm. The prosecution filed the indictment for homicide.
                """;

        List<KeywordScore> scores = service.computeScores(text);
        assertThat(scores).isNotEmpty();
        List<String> keywords = scores.stream().map(KeywordScore::getKeyword).toList();
        assertThat(keywords).contains("homicide", "suspect", "evidence", "witness", "murder");
    }

    @Test
    void homicideAppearsMoreOftenThanCrime() {
        // "homicide" appears 2x, should be ranked; scores are sorted desc
        String text = "The homicide report noted homicide as the cause. A general crime was referenced.";
        List<KeywordScore> scores = service.computeScores(text);
        KeywordScore homicide = scores.stream()
                .filter(k -> k.getKeyword().equals("homicide"))
                .findFirst().orElseThrow();
        KeywordScore crime = scores.stream()
                .filter(k -> k.getKeyword().equals("crime"))
                .findFirst().orElseThrow();
        assertThat(homicide.getScore()).isGreaterThan(crime.getScore());
        assertThat(homicide.getFrequency()).isEqualTo(2);
    }

    @Test
    void topNLimitsResults() {
        String text = "homicide murder assault burglary theft larceny robbery arson fraud forgery";
        List<KeywordScore> top3 = service.topN(text, 3);
        assertThat(top3).hasSize(3);
    }
}
