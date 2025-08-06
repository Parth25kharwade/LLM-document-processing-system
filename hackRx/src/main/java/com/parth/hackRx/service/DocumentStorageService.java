package com.parth.hackRx.service;

import com.parth.hackRx.model.Document;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface DocumentStorageService {
    Document storeDocument(MultipartFile file);
    List<Document> getAllDocuments();
    Document getDocumentById(Long id);
}