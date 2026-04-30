package com.example.pdfclassifier.service;

import com.example.pdfclassifier.entity.PdfDocument.DocumentQuality;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.Map;

@Service
@Slf4j
public class MlClassificationService {

    private final RestTemplate restTemplate;
    private final String mlServiceBaseUrl;

    public MlClassificationService(RestTemplate restTemplate,
                                   @Value("${app.ml.service.base-url}") String mlServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.mlServiceBaseUrl = mlServiceBaseUrl;
    }

    public String classify(String filePath, DocumentQuality quality) {
        String endpoint = quality == DocumentQuality.POOR
                ? "/classify/donut"
                : "/classify/layoutlm";

        String url = mlServiceBaseUrl + endpoint;
        log.info("Calling ML service: {} for file: {}", url, filePath);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(new File(filePath)));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class);

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            throw new RuntimeException("Empty response from ML service");
        }

        return formatClassificationResult(responseBody, quality);
    }

    @SuppressWarnings("unchecked")
    private String formatClassificationResult(Map<String, Object> response, DocumentQuality quality) {
        StringBuilder result = new StringBuilder();
        result.append("Classification Results:\n");
        result.append("======================\n\n");

        String model = quality == DocumentQuality.POOR ? "Donut (OCR-free)" : "LayoutLMv3 (OCR-based)";
        result.append("Model: ").append(model).append("\n");

        String documentType = (String) response.getOrDefault("document_type", "UNKNOWN");
        result.append("Document Type: ").append(documentType.toUpperCase()).append("\n");

        Object confidence = response.getOrDefault("confidence", 0.0);
        if (confidence instanceof Number) {
            double pct = ((Number) confidence).doubleValue() * 100;
            result.append("Confidence: ").append(String.format("%.1f%%", pct)).append("\n");
        }

        Object details = response.get("details");
        if (details instanceof Map) {
            Map<String, Object> detailsMap = (Map<String, Object>) details;
            if (!detailsMap.isEmpty()) {
                result.append("\nDetails:\n");
                detailsMap.forEach((key, value) ->
                        result.append("  ").append(key).append(": ").append(value).append("\n"));
            }
        }

        return result.toString();
    }
}
