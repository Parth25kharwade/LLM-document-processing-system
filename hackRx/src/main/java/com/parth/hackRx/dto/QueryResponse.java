package com.parth.hackRx.dto;

import lombok.Data;

import java.util.List;

@Data
public class QueryResponse {

    private String decision;
    private double amount;
    private String justification;
    private List<ClauseReference> clauses;

    @Data
    public static class ClauseReference {
        private String section;
        private String text;
        private double relevanceScore;

        // These fields are populated after the initial JSON parsing
        // to enrich the response with source document information.
        private Long documentId;
        private String pageRange;
    }
}