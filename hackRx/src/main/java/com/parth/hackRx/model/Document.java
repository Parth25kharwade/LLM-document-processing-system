package com.parth.hackRx.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.Date;

@Entity
@Data
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;
    private String fileType;
    private String filePath;

    @Lob
    private String content;

    @Temporal(TemporalType.TIMESTAMP)
    private Date uploadDate;

    @Enumerated(EnumType.STRING)
    private DocumentStatus status;

    public enum DocumentStatus {
        UPLOADED, PROCESSED, FAILED
    }
}