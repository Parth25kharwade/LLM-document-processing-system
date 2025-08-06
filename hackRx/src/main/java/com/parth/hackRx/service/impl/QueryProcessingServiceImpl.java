package com.parth.hackRx.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parth.hackRx.dto.QueryRequest;
import com.parth.hackRx.dto.QueryResponse;
import com.parth.hackRx.model.DocumentChunk;
import com.parth.hackRx.repository.DocumentChunkRepository;
import com.parth.hackRx.service.QueryProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class QueryProcessingServiceImpl implements QueryProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(QueryProcessingServiceImpl.class);
    private static final int MAX_RELEVANT_CHUNKS = 5;

    private final DocumentChunkRepository documentChunkRepository;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final EmbeddingModel embeddingModel;

    public QueryProcessingServiceImpl(DocumentChunkRepository documentChunkRepository,
                                      ChatClient chatClient,
                                      ObjectMapper objectMapper,
                                      EmbeddingModel embeddingModel) {
        this.documentChunkRepository = documentChunkRepository;
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public QueryResponse processQuery(QueryRequest request) {
        try {
            byte[] queryEmbedding = generateEmbedding(request.getQuery());

            List<DocumentChunk> relevantChunks = findMostRelevantChunks(queryEmbedding);
            String context = buildContext(relevantChunks);

            String prompt = buildPrompt(request.getQuery(), context, request.getLanguage());
            String llmResponse = generateResponse(prompt);

            return parseGeminiResponse(llmResponse, relevantChunks);
        } catch (IOException e) {
            logger.error("Failed to process query: {}", request.getQuery(), e);
            return buildErrorResponse("Processing failed due to an internal error");
        }
    }

    private List<DocumentChunk> findMostRelevantChunks(byte[] queryEmbedding) {
        return documentChunkRepository.findAll().stream()
                .sorted(Comparator.comparingDouble(
                        (DocumentChunk chunk) -> calculateSimilarity(chunk.getEmbedding(), queryEmbedding)
                ).reversed())
                .limit(MAX_RELEVANT_CHUNKS)
                .collect(Collectors.toList());
    }

    private double calculateSimilarity(byte[] embedding1, byte[] embedding2) {
        if (embedding1 == null || embedding2 == null || embedding1.length != embedding2.length) {
            return 0.0;
        }
        
        // Convert byte arrays back to double arrays
        double[] vector1 = byteArrayToDoubleArray(embedding1);
        double[] vector2 = byteArrayToDoubleArray(embedding2);
        
        // Calculate cosine similarity
        return cosineSimilarity(vector1, vector2);
    }
    
    private double cosineSimilarity(double[] vectorA, double[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String buildContext(List<DocumentChunk> chunks) {
        return chunks.stream()
                .map(chunk -> String.format("""
                        [Document: %s, Section: %s]
                        %s
                        """,
                        chunk.getDocument().getFileName(),
                        chunk.getChunkIndex(),
                        chunk.getText()))
                .collect(Collectors.joining("\n"));
    }

    private String buildPrompt(String query, String context, String language) {
        String jsonTemplate = """
                {
                    "decision": "approved|rejected|needs_review",
                    "amount": 0.0,
                    "justification": "string",
                    "clauses": [
                        {
                            "section": "string",
                            "text": "string",
                            "relevanceScore": 0.0
                        }
                    ]
                }""";

        if ("hi".equals(language)) {
            return String.format("""
                    निम्नलिखित बीमा पॉलिसी संदर्भ का विश्लेषण करें और प्रश्न का उत्तर दें।
                    संदर्भ: %s
                    प्रश्न: %s
                    
                    निम्नलिखित JSON प्रारूप में उत्तर दें:
                    %s
                    """, context, query, jsonTemplate);
        }

        return String.format("""
                Analyze the following insurance policy context and answer the question.
                Context: %s
                Question: %s
                
                Respond in exactly this JSON format:
                %s
                """, context, query, jsonTemplate);
    }

    private String generateResponse(String prompt) throws IOException {
        try {
            // Use the auto-configured ChatClient for a more idiomatic Spring AI approach
            return chatClient.call(prompt);
        } catch (Exception e) {
            logger.error("Failed to generate response from Gemini", e);
            throw new IOException("Failed to get response from Gemini API", e);
        }
    }

    private QueryResponse parseGeminiResponse(String llmResponse, List<DocumentChunk> relevantChunks) {
        try {
            QueryResponse response = objectMapper.readValue(llmResponse, QueryResponse.class);

            // Enhance response with document references
            if (response.getClauses() != null) {
                response.getClauses().forEach(clause -> {
                    relevantChunks.stream()
                            .filter(chunk -> chunk.getText().contains(clause.getSection()))
                            .findFirst()
                            .ifPresent(chunk -> {
                                clause.setDocumentId(chunk.getDocument().getId());
                                clause.setPageRange(chunk.getStartPage() + "-" + chunk.getEndPage());
                            });
                });
            }

            return response;
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse Gemini response: {}", llmResponse, e);
            return buildErrorResponse("Failed to parse AI response");
        }
    }

    private QueryResponse buildErrorResponse(String message) {
        QueryResponse response = new QueryResponse();
        response.setDecision("error");
        response.setJustification(message);
        return response;
    }

    private byte[] generateEmbedding(String text) throws IOException {
        try {
            EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);
            EmbeddingResponse response = embeddingModel.call(request);
            
            if (response.getResults().isEmpty()) {
                throw new IOException("No embedding generated for text");
            }
            
            // Spring AI 0.8.1 returns a List<Double>
            List<Double> embedding = response.getResults().get(0).getOutput().getEmbedding();
            return doubleListToByteArray(embedding);
        } catch (Exception e) {
            logger.error("Failed to generate embedding for text: {}", text, e);
            throw new IOException("Embedding generation failed", e);
        }
    }
    
    /**
     * Converts a List of Doubles to a byte array using ByteBuffer for robust serialization.
     */
    private byte[] doubleListToByteArray(List<Double> list) {
        double[] doubles = list.stream().mapToDouble(Double::doubleValue).toArray();
        ByteBuffer byteBuffer = ByteBuffer.allocate(doubles.length * 8);
        for (double d : doubles) {
            byteBuffer.putDouble(d);
        }
        return byteBuffer.array();
    }

    /**
     * Converts a byte array back to a double array using ByteBuffer.
     */
    private double[] byteArrayToDoubleArray(byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        double[] doubles = new double[bytes.length / 8];
        for (int i = 0; i < doubles.length; i++) {
            doubles[i] = byteBuffer.getDouble(i * 8);
        }
        return doubles;
    }
}