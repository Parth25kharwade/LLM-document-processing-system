package com.parth.hackRx.controller;

import com.parth.hackRx.dto.QueryRequest;
import com.parth.hackRx.dto.QueryResponse;
import com.parth.hackRx.service.QueryProcessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/query")
public class QueryController {

    private final QueryProcessingService queryProcessingService;

    public QueryController(QueryProcessingService queryProcessingService) {
        this.queryProcessingService = queryProcessingService;
    }

    @PostMapping
    public ResponseEntity<QueryResponse> processQuery(@RequestBody QueryRequest request) {
        QueryResponse response = queryProcessingService.processQuery(request);
        return ResponseEntity.ok(response);
    }
}