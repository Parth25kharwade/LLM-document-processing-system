package com.parth.hackRx.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class DocumentChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Document document;

    private int chunkIndex;
    private int startPage;
    private int endPage;

    @Lob
    private String text;

    @Lob
    private byte[] embedding;
}