package com.example.pdfclassifier.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "pdf_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PdfDocument {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;
    
    @Column(name = "stored_filename", nullable = false)
    private String storedFilename;
    
    @Column(name = "file_path", nullable = false)
    private String filePath;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "upload_date")
    private LocalDateTime uploadDate;
    
    @Column(name = "processing_status")
    @Enumerated(EnumType.STRING)
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    @Column(name = "document_quality")
    @Enumerated(EnumType.STRING)
    private DocumentQuality documentQuality = DocumentQuality.GOOD;
    
    @Column(name = "classification_result", columnDefinition = "TEXT")
    private String classificationResult;
    
    @Column(name = "processed_file_path")
    private String processedFilePath;
    
    @Column(name = "processed_date")
    private LocalDateTime processedDate;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @PrePersist
    protected void onCreate() {
        uploadDate = LocalDateTime.now();
    }
    
    public enum ProcessingStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    public enum DocumentQuality {
        GOOD,
        POOR
    }
}
