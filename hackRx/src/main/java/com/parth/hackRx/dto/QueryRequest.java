package com.parth.hackRx.dto;

import lombok.Data;

@Data
public class QueryRequest {
    private String query;
    private String language = "en"; // Default to English
}