package com.parth.hackRx.service;

import com.parth.hackRx.model.Document;

import java.io.IOException;

public interface DocumentProcessingService {
    void processDocument(Document document) throws IOException;
}