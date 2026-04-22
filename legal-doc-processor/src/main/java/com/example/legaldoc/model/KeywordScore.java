package com.example.legaldoc.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeywordScore {
    private String keyword;
    private double score;
    private int frequency;
}
