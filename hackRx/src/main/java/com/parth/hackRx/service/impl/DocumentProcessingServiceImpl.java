package com.parth.hackRx.service.impl;


import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.parth.hackRx.model.Document;
import com.parth.hackRx.model.DocumentChunk;
import com.parth.hackRx.repository.DocumentChunkRepository;
import com.parth.hackRx.service.DocumentProcessingService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentProcessingServiceImpl implements DocumentProcessingService {

    private final DocumentChunkRepository documentChunkRepository;
    private final VertexAI vertexAI;
    private final String modelName;
    private final EmbeddingModel embeddingModel;

    public DocumentProcessingServiceImpl(DocumentChunkRepository documentChunkRepository,
                                         VertexAI vertexAI,
                                         @Value("${google.cloud.vertexai.model}") String modelName,
                                         EmbeddingModel embeddingModel) {
        this.documentChunkRepository = documentChunkRepository;
        this.vertexAI = vertexAI;
        this.modelName = modelName;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void processDocument(Document document) throws IOException {
        String filePath = document.getFilePath();
        String content = extractTextFromFile(filePath, document.getFileType());

        // Split document into chunks (simplified)
        List<String> chunks = splitTextIntoChunks(content, 1000);

        // Process each chunk
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);

            // Generate embedding for the chunk
            byte[] embedding = generateEmbedding(chunkText);

            // Save chunk to database
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(document);
            chunk.setChunkIndex(i);
            chunk.setText(chunkText);
            chunk.setEmbedding(embedding);

            documentChunkRepository.save(chunk);
        }

        // Update document status
        document.setStatus(Document.DocumentStatus.PROCESSED);
    }

    private String extractTextFromFile(String filePath, String fileType) throws IOException {
        if (fileType.contains("pdf")) {
            // For PDFBox 3.x (newer versions)
            try (PDDocument document = Loader.loadPDF(new File(filePath))) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            }
        } else if (fileType.contains("wordprocessingml")) {
            try (FileInputStream fis = new FileInputStream(filePath);
                 XWPFDocument document = new XWPFDocument(fis);
                 XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                return extractor.getText();
            }
        } else if (fileType.contains("text") || fileType.contains("plain")) {
            return Files.readString(Path.of(filePath));
        } else {
            throw new IOException("Unsupported file type: " + fileType);
        }
    }

    private List<String> splitTextIntoChunks(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();

        for (int i = 0; i < length; i += chunkSize) {
            chunks.add(text.substring(i, Math.min(length, i + chunkSize)));
        }

        return chunks;
    }

    public byte[] generateEmbedding(String text) throws IOException {
        try {
            EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);
            EmbeddingResponse response = embeddingModel.call(request);
            
            if (response.getResults().isEmpty()) {
                throw new IOException("No embedding generated for text");
            }
            
            // Convert float array to byte array
            float[] embedding = response.getResults().get(0).getOutput();
            return floatArrayToByteArray(embedding);
        } catch (Exception e) {
            throw new IOException("Embedding generation failed", e);
        }
    }
    
    private byte[] floatArrayToByteArray(float[] floats) {
        byte[] bytes = new byte[floats.length * 4];
        for (int i = 0; i < floats.length; i++) {
            int bits = Float.floatToIntBits(floats[i]);
            bytes[i * 4] = (byte) (bits & 0xff);
            bytes[i * 4 + 1] = (byte) ((bits >> 8) & 0xff);
            bytes[i * 4 + 2] = (byte) ((bits >> 16) & 0xff);
            bytes[i * 4 + 3] = (byte) ((bits >> 24) & 0xff);
        }
        return bytes;
    }
}