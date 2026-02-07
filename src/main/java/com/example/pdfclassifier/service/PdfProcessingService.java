package com.example.pdfclassifier.service;

import com.example.pdfclassifier.entity.PdfDocument;
import com.example.pdfclassifier.entity.User;
import com.example.pdfclassifier.repository.PdfDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfProcessingService {
    
    private final PdfDocumentRepository pdfDocumentRepository;
    private final EmailService emailService;
    
    @Value("${app.upload.dir}")
    private String uploadDir;
    
    @Value("${app.processed.dir}")
    private String processedDir;
    
    /**
     * Validate PDF file
     */
    public boolean validatePdfFile(MultipartFile file) {
        if (file.isEmpty()) {
            return false;
        }
        
        // Check file extension
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            return false;
        }
        
        // Check MIME type using Apache Tika for robust detection
        try {
            Tika tika = new Tika();
            String detectedType = tika.detect(file.getInputStream());
            return "application/pdf".equals(detectedType);
        } catch (IOException e) {
            log.error("Error validating PDF file", e);
            return false;
        }
    }
    
    /**
     * Save uploaded PDF file
     */
    @Transactional
    public PdfDocument saveUploadedFile(MultipartFile file, User user) throws IOException {
        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String storedFilename = UUID.randomUUID().toString() + "_" + originalFilename;
        Path filePath = uploadPath.resolve(storedFilename);
        
        // Save file
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        // Create database record
        PdfDocument pdfDocument = new PdfDocument();
        pdfDocument.setOriginalFilename(originalFilename);
        pdfDocument.setStoredFilename(storedFilename);
        pdfDocument.setFilePath(filePath.toString());
        pdfDocument.setFileSize(file.getSize());
        pdfDocument.setUser(user);
        pdfDocument.setProcessingStatus(PdfDocument.ProcessingStatus.PENDING);
        
        return pdfDocumentRepository.save(pdfDocument);
    }
    
    /**
     * Process PDF asynchronously with ML classification
     */
    @Async
    @Transactional
    public void processPdfAsync(Long documentId) {
        PdfDocument document = pdfDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        try {
            document.setProcessingStatus(PdfDocument.ProcessingStatus.PROCESSING);
            pdfDocumentRepository.save(document);
            
            // Extract text and images from PDF
            File pdfFile = new File(document.getFilePath());
            ExtractedContent content = extractContentFromPdf(pdfFile);
            
            // Perform ML classification (placeholder - implement your ML logic here)
            String classificationResult = performMLClassification(content);
            
            // Create processed file
            String processedFilePath = createProcessedFile(document, classificationResult);
            
            // Update document status
            document.setClassificationResult(classificationResult);
            document.setProcessedFilePath(processedFilePath);
            document.setProcessingStatus(PdfDocument.ProcessingStatus.COMPLETED);
            document.setProcessedDate(LocalDateTime.now());
            pdfDocumentRepository.save(document);
            
            // Send email notification
            emailService.sendProcessingCompleteEmail(document);
            
            log.info("Successfully processed PDF document: {}", documentId);
            
        } catch (Exception e) {
            log.error("Error processing PDF document: {}", documentId, e);
            document.setProcessingStatus(PdfDocument.ProcessingStatus.FAILED);
            document.setErrorMessage(e.getMessage());
            pdfDocumentRepository.save(document);
        }
    }
    
    /**
     * Extract text and images from PDF
     */
    private ExtractedContent extractContentFromPdf(File pdfFile) throws IOException {
        ExtractedContent content = new ExtractedContent();
        
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            // Extract text
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            content.setText(text);
            
            // Extract images from pages
            PDFRenderer renderer = new PDFRenderer(document);
            List<BufferedImage> images = new ArrayList<>();
            
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, 150);
                images.add(image);
            }
            content.setImages(images);
        }
        
        return content;
    }
    
    /**
     * Perform ML classification on extracted content
     * PLACEHOLDER: Implement your actual ML classification logic here
     * 
     * Options:
     * 1. Call external ML API (AWS Rekognition, Google Vision, Azure)
     * 2. Use DL4J for embedded ML
     * 3. Call Python ML service via REST
     */
    private String performMLClassification(ExtractedContent content) {
        // This is a placeholder implementation
        // Replace with your actual ML classification logic
        
        log.info("Performing ML classification on content...");
        
        StringBuilder result = new StringBuilder();
        result.append("Classification Results:\n");
        result.append("======================\n\n");
        
        // Example classification based on text content
        String text = content.getText();
        
        if (text.toLowerCase().contains("invoice") || text.toLowerCase().contains("bill")) {
            result.append("Document Type: INVOICE\n");
            result.append("Confidence: 85%\n");
        } else if (text.toLowerCase().contains("contract") || text.toLowerCase().contains("agreement")) {
            result.append("Document Type: CONTRACT\n");
            result.append("Confidence: 90%\n");
        } else if (text.toLowerCase().contains("resume") || text.toLowerCase().contains("curriculum")) {
            result.append("Document Type: RESUME\n");
            result.append("Confidence: 75%\n");
        } else {
            result.append("Document Type: GENERAL\n");
            result.append("Confidence: 60%\n");
        }
        
        result.append("\nText Length: ").append(text.length()).append(" characters\n");
        result.append("Number of Pages: ").append(content.getImages().size()).append("\n");
        
        // TODO: Implement actual ML classification
        // Example approaches:
        // 1. REST call to Python ML service
        // 2. Use pre-trained model with DL4J
        // 3. Call cloud ML API
        
        return result.toString();
    }
    
    /**
     * Create processed file with classification results
     */
    private String createProcessedFile(PdfDocument document, String classificationResult) throws IOException {
        Path processedPath = Paths.get(processedDir);
        if (!Files.exists(processedPath)) {
            Files.createDirectories(processedPath);
        }
        
        String processedFilename = "processed_" + document.getStoredFilename().replace(".pdf", ".txt");
        Path outputPath = processedPath.resolve(processedFilename);
        
        // Write classification results to file
        Files.writeString(outputPath, classificationResult);
        
        return outputPath.toString();
    }
    
    /**
     * Get all documents for a user
     */
    public List<PdfDocument> getUserDocuments(User user) {
        return pdfDocumentRepository.findByUser(user);
    }
    
    /**
     * Inner class to hold extracted content
     */
    private static class ExtractedContent {
        private String text;
        private List<BufferedImage> images;
        
        public String getText() {
            return text;
        }
        
        public void setText(String text) {
            this.text = text;
        }
        
        public List<BufferedImage> getImages() {
            return images;
        }
        
        public void setImages(List<BufferedImage> images) {
            this.images = images;
        }
    }
}
