package com.parth.hackRx.service;


import com.parth.hackRx.dto.QueryRequest;
import com.parth.hackRx.dto.QueryResponse;

public interface QueryProcessingService {
    QueryResponse processQuery(QueryRequest request);
}
