package com.parth.hackRx.service.impl;


import com.parth.hackRx.model.Document;
import com.parth.hackRx.repository.DocumentRepository;
import com.parth.hackRx.service.DocumentStorageService;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

@Service
public class DocumentStorageServiceImpl implements DocumentStorageService {

    private final DocumentRepository documentRepository;
    private final Path fileStorageLocation;
    private final Tika tika = new Tika();

    public DocumentStorageServiceImpl(DocumentRepository documentRepository,
                                      @Value("${app.file.storage.location}") String fileStorageLocation) {
        this.documentRepository = documentRepository;
        this.fileStorageLocation = Paths.get(fileStorageLocation).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (IOException ex) {
            throw new RuntimeException("Could not create upload directory", ex);
        }
    }

    @Override
    public Document storeDocument(MultipartFile file) {
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();

        try {
            // Save file to storage
            Path targetLocation = fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation);

            // Create document record
            Document document = new Document();
            document.setFileName(file.getOriginalFilename());
            document.setFileType(tika.detect(file.getBytes()));
            document.setFilePath(targetLocation.toString());
            document.setUploadDate(new Date());
            document.setStatus(Document.DocumentStatus.UPLOADED);

            return documentRepository.save(document);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to store file", ex);
        }
    }

    @Override
    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }

    @Override
    public Document getDocumentById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));
    }
}