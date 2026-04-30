package com.example.pdfclassifier.util;

import com.example.pdfclassifier.entity.PdfDocument;
import com.example.pdfclassifier.entity.User;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;

public final class TestDataFactory {

    private TestDataFactory() {}

    // ── PDF generation ──────────────────────────────────────────

    public static byte[] createValidPdfBytes(String textContent) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(textContent);
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    public static byte[] createValidPdfBytes() throws IOException {
        return createValidPdfBytes("Hello, this is a test PDF document.");
    }

    public static MockMultipartFile createPdfMultipartFile(String name, String originalFilename, byte[] content) {
        return new MockMultipartFile(name, originalFilename, "application/pdf", content);
    }

    public static MockMultipartFile createPdfMultipartFile() throws IOException {
        return createPdfMultipartFile("file", "test.pdf", createValidPdfBytes());
    }

    public static MockMultipartFile createEmptyMultipartFile() {
        return new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
    }

    public static MockMultipartFile createNonPdfMultipartFile() {
        return new MockMultipartFile("file", "readme.txt", "text/plain", "not a pdf".getBytes());
    }

    // ── Entity builders ─────────────────────────────────────────

    public static User createUser(String username, String email, String encodedPassword) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(encodedPassword);
        user.setEnabled(true);
        user.set2faEnabled(false);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    public static User createUser() {
        return createUser("testuser", "test@example.com", "$2a$10$encodedPassword");
    }

    public static PdfDocument createPdfDocument(User user) {
        PdfDocument doc = new PdfDocument();
        doc.setId(1L);
        doc.setOriginalFilename("test.pdf");
        doc.setStoredFilename("uuid_test.pdf");
        doc.setFilePath("/tmp/uploads/uuid_test.pdf");
        doc.setFileSize(1024L);
        doc.setUser(user);
        doc.setProcessingStatus(PdfDocument.ProcessingStatus.PENDING);
        doc.setDocumentQuality(PdfDocument.DocumentQuality.GOOD);
        doc.setUploadDate(LocalDateTime.now());
        return doc;
    }

    public static PdfDocument createCompletedPdfDocument(User user) {
        PdfDocument doc = createPdfDocument(user);
        doc.setProcessingStatus(PdfDocument.ProcessingStatus.COMPLETED);
        doc.setClassificationResult("Classification Results:\nDocument Type: INVOICE\nConfidence: 85%");
        doc.setProcessedFilePath("/tmp/processed/processed_uuid_test.txt");
        doc.setProcessedDate(LocalDateTime.now());
        return doc;
    }
}
