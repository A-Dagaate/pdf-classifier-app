package com.example.pdfclassifier.service;

import com.example.pdfclassifier.entity.PdfDocument.DocumentQuality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MlClassificationServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private MlClassificationService service;

    @TempDir
    Path tempDir;

    private String testFilePath;

    @BeforeEach
    void setUp() throws IOException {
        service = new MlClassificationService(restTemplate, "http://localhost:8000");
        Path testFile = tempDir.resolve("test.pdf");
        Files.writeString(testFile, "dummy pdf content");
        testFilePath = testFile.toString();
    }

    @Test
    void classify_goodQuality_callsLayoutlmEndpoint() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("document_type", "invoice");
        body.put("confidence", 0.95);
        body.put("model", "layoutlmv3");
        body.put("details", Map.of("invoice", "0.9500"));

        when(restTemplate.exchange(
                eq("http://localhost:8000/classify/layoutlm"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        String result = service.classify(testFilePath, DocumentQuality.GOOD);

        assertThat(result).contains("LayoutLMv3");
        assertThat(result).contains("INVOICE");
        assertThat(result).contains("95.0%");
    }

    @Test
    void classify_poorQuality_callsDonutEndpoint() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("document_type", "letter");
        body.put("confidence", 1.0);
        body.put("model", "donut");
        body.put("details", Map.of("raw_output", "<s>letter</s>"));

        when(restTemplate.exchange(
                eq("http://localhost:8000/classify/donut"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        String result = service.classify(testFilePath, DocumentQuality.POOR);

        assertThat(result).contains("Donut");
        assertThat(result).contains("LETTER");
        assertThat(result).contains("100.0%");
    }

    @Test
    void classify_emptyResponseBody_throws() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        assertThatThrownBy(() -> service.classify(testFilePath, DocumentQuality.GOOD))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Empty response");
    }

    @Test
    void classify_restClientException_propagates() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("Connection refused"));

        assertThatThrownBy(() -> service.classify(testFilePath, DocumentQuality.GOOD))
                .isInstanceOf(RestClientException.class)
                .hasMessageContaining("Connection refused");
    }

    @Test
    void classify_formatsDetailsSection() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("invoice", "0.8000");
        details.put("letter", "0.1500");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("document_type", "invoice");
        body.put("confidence", 0.80);
        body.put("model", "layoutlmv3");
        body.put("details", details);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        String result = service.classify(testFilePath, DocumentQuality.GOOD);

        assertThat(result).contains("Details:");
        assertThat(result).contains("invoice: 0.8000");
        assertThat(result).contains("letter: 0.1500");
    }

    @Test
    void classify_noDetailsSection() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("document_type", "resume");
        body.put("confidence", 0.75);
        body.put("model", "layoutlmv3");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        String result = service.classify(testFilePath, DocumentQuality.GOOD);

        assertThat(result).contains("RESUME");
        assertThat(result).doesNotContain("Details:");
    }
}
