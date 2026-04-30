package com.example.pdfclassifier.service;

import com.example.pdfclassifier.entity.PdfDocument;
import com.example.pdfclassifier.entity.PdfDocument.DocumentQuality;
import com.example.pdfclassifier.entity.User;
import com.example.pdfclassifier.repository.PdfDocumentRepository;
import com.example.pdfclassifier.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdfProcessingServiceTest {

    @Mock
    private PdfDocumentRepository pdfDocumentRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private MlClassificationService mlClassificationService;

    @InjectMocks
    private PdfProcessingService pdfProcessingService;

    @TempDir
    Path tempDir;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = TestDataFactory.createUser();
        ReflectionTestUtils.setField(pdfProcessingService, "uploadDir", tempDir.resolve("uploads").toString());
        ReflectionTestUtils.setField(pdfProcessingService, "processedDir", tempDir.resolve("processed").toString());
    }

    // ── validatePdfFile ─────────────────────────────────────────

    @Test
    void validatePdfFile_validPdf_returnsTrue() throws IOException {
        MockMultipartFile file = TestDataFactory.createPdfMultipartFile();
        assertThat(pdfProcessingService.validatePdfFile(file)).isTrue();
    }

    @Test
    void validatePdfFile_emptyFile_returnsFalse() {
        MockMultipartFile file = TestDataFactory.createEmptyMultipartFile();
        assertThat(pdfProcessingService.validatePdfFile(file)).isFalse();
    }

    @Test
    void validatePdfFile_wrongExtension_returnsFalse() {
        MockMultipartFile file = TestDataFactory.createNonPdfMultipartFile();
        assertThat(pdfProcessingService.validatePdfFile(file)).isFalse();
    }

    @Test
    void validatePdfFile_pdfExtensionButNotPdfContent_returnsFalse() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf".getBytes());
        assertThat(pdfProcessingService.validatePdfFile(file)).isFalse();
    }

    @Test
    void validatePdfFile_nullFilename_returnsFalse() {
        MockMultipartFile file = new MockMultipartFile("file", null, "application/pdf", "data".getBytes());
        assertThat(pdfProcessingService.validatePdfFile(file)).isFalse();
    }

    // ── saveUploadedFile ────────────────────────────────────────

    @Test
    void saveUploadedFile_createsFileAndRecord() throws IOException {
        MockMultipartFile file = TestDataFactory.createPdfMultipartFile();
        when(pdfDocumentRepository.save(any(PdfDocument.class))).thenAnswer(inv -> {
            PdfDocument doc = inv.getArgument(0);
            doc.setId(1L);
            return doc;
        });

        PdfDocument result = pdfProcessingService.saveUploadedFile(file, testUser, DocumentQuality.GOOD);

        assertThat(result.getOriginalFilename()).isEqualTo("test.pdf");
        assertThat(result.getUser()).isEqualTo(testUser);
        assertThat(result.getProcessingStatus()).isEqualTo(PdfDocument.ProcessingStatus.PENDING);
        assertThat(result.getDocumentQuality()).isEqualTo(DocumentQuality.GOOD);
        assertThat(Files.exists(Path.of(result.getFilePath()))).isTrue();
    }

    @Test
    void saveUploadedFile_poorQuality_setsQuality() throws IOException {
        MockMultipartFile file = TestDataFactory.createPdfMultipartFile();
        when(pdfDocumentRepository.save(any(PdfDocument.class))).thenAnswer(inv -> {
            PdfDocument doc = inv.getArgument(0);
            doc.setId(2L);
            return doc;
        });

        PdfDocument result = pdfProcessingService.saveUploadedFile(file, testUser, DocumentQuality.POOR);

        assertThat(result.getDocumentQuality()).isEqualTo(DocumentQuality.POOR);
    }

    // ── processPdfAsync ─────────────────────────────────────────

    @Test
    void processPdfAsync_mlSuccess_completesDocument() throws IOException {
        // Create a real PDF file for processing
        byte[] pdfBytes = TestDataFactory.createValidPdfBytes("Invoice content for testing");
        Path pdfFile = tempDir.resolve("test.pdf");
        Files.write(pdfFile, pdfBytes);

        PdfDocument doc = TestDataFactory.createPdfDocument(testUser);
        doc.setFilePath(pdfFile.toString());

        when(pdfDocumentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(pdfDocumentRepository.save(any(PdfDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mlClassificationService.classify(anyString(), any(DocumentQuality.class)))
                .thenReturn("Classification Results:\nDocument Type: INVOICE\nConfidence: 95.0%");

        pdfProcessingService.processPdfAsync(1L);

        ArgumentCaptor<PdfDocument> captor = ArgumentCaptor.forClass(PdfDocument.class);
        verify(pdfDocumentRepository, atLeast(2)).save(captor.capture());

        PdfDocument saved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(saved.getProcessingStatus()).isEqualTo(PdfDocument.ProcessingStatus.COMPLETED);
        assertThat(saved.getClassificationResult()).contains("INVOICE");
        assertThat(saved.getProcessedDate()).isNotNull();
        verify(emailService).sendProcessingCompleteEmail(any());
    }

    @Test
    void processPdfAsync_mlFails_fallsBackToRuleBased() throws IOException {
        byte[] pdfBytes = TestDataFactory.createValidPdfBytes("This is an invoice document");
        Path pdfFile = tempDir.resolve("test.pdf");
        Files.write(pdfFile, pdfBytes);

        PdfDocument doc = TestDataFactory.createPdfDocument(testUser);
        doc.setFilePath(pdfFile.toString());

        when(pdfDocumentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(pdfDocumentRepository.save(any(PdfDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mlClassificationService.classify(anyString(), any(DocumentQuality.class)))
                .thenThrow(new RuntimeException("ML service down"));

        pdfProcessingService.processPdfAsync(1L);

        ArgumentCaptor<PdfDocument> captor = ArgumentCaptor.forClass(PdfDocument.class);
        verify(pdfDocumentRepository, atLeast(2)).save(captor.capture());

        PdfDocument saved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(saved.getProcessingStatus()).isEqualTo(PdfDocument.ProcessingStatus.COMPLETED);
        assertThat(saved.getClassificationResult()).contains("rule-based fallback");
    }

    @Test
    void processPdfAsync_totalFailure_marksFailed() {
        PdfDocument doc = TestDataFactory.createPdfDocument(testUser);
        doc.setFilePath("/nonexistent/path/test.pdf");

        when(pdfDocumentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(pdfDocumentRepository.save(any(PdfDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mlClassificationService.classify(anyString(), any(DocumentQuality.class)))
                .thenThrow(new RuntimeException("ML service down"));

        pdfProcessingService.processPdfAsync(1L);

        ArgumentCaptor<PdfDocument> captor = ArgumentCaptor.forClass(PdfDocument.class);
        verify(pdfDocumentRepository, atLeast(2)).save(captor.capture());

        PdfDocument saved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(saved.getProcessingStatus()).isEqualTo(PdfDocument.ProcessingStatus.FAILED);
        assertThat(saved.getErrorMessage()).isNotNull();
    }

    @Test
    void processPdfAsync_documentNotFound_throws() {
        when(pdfDocumentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pdfProcessingService.processPdfAsync(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Document not found");
    }

    // ── getUserDocuments ────────────────────────────────────────

    @Test
    void getUserDocuments_returnsList() {
        PdfDocument doc1 = TestDataFactory.createPdfDocument(testUser);
        PdfDocument doc2 = TestDataFactory.createPdfDocument(testUser);
        doc2.setId(2L);
        when(pdfDocumentRepository.findByUser(testUser)).thenReturn(List.of(doc1, doc2));

        List<PdfDocument> result = pdfProcessingService.getUserDocuments(testUser);

        assertThat(result).hasSize(2);
    }

    @Test
    void getUserDocuments_emptyList() {
        when(pdfDocumentRepository.findByUser(testUser)).thenReturn(List.of());

        List<PdfDocument> result = pdfProcessingService.getUserDocuments(testUser);

        assertThat(result).isEmpty();
    }
}
