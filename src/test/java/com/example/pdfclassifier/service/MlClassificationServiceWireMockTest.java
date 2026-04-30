package com.example.pdfclassifier.service;

import com.example.pdfclassifier.entity.PdfDocument.DocumentQuality;
import com.example.pdfclassifier.util.TestDataFactory;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MlClassificationServiceWireMockTest {

    private WireMockServer wireMockServer;
    private MlClassificationService service;

    @TempDir
    Path tempDir;
    private String testFilePath;

    @BeforeEach
    void setUp() throws IOException {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        RestTemplate restTemplate = new RestTemplate();
        service = new MlClassificationService(restTemplate, "http://localhost:" + wireMockServer.port());

        Path testFile = tempDir.resolve("test.pdf");
        Files.write(testFile, TestDataFactory.createValidPdfBytes());
        testFilePath = testFile.toString();
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void classify_layoutlm_returnsFormattedResult() {
        stubFor(post(urlEqualTo("/classify/layoutlm"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"document_type\":\"invoice\",\"confidence\":0.92,\"model\":\"layoutlmv3\",\"details\":{\"invoice\":\"0.9200\"}}")));

        String result = service.classify(testFilePath, DocumentQuality.GOOD);

        assertThat(result).contains("LayoutLMv3");
        assertThat(result).contains("INVOICE");
        assertThat(result).contains("92.0%");
        wireMockServer.verify(postRequestedFor(urlEqualTo("/classify/layoutlm")));
    }

    @Test
    void classify_donut_returnsFormattedResult() {
        stubFor(post(urlEqualTo("/classify/donut"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"document_type\":\"letter\",\"confidence\":1.0,\"model\":\"donut\",\"details\":{\"raw_output\":\"<s>letter</s>\"}}")));

        String result = service.classify(testFilePath, DocumentQuality.POOR);

        assertThat(result).contains("Donut");
        assertThat(result).contains("LETTER");
        wireMockServer.verify(postRequestedFor(urlEqualTo("/classify/donut")));
    }

    @Test
    void classify_serverError_throwsException() {
        stubFor(post(urlEqualTo("/classify/layoutlm"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

        assertThatThrownBy(() -> service.classify(testFilePath, DocumentQuality.GOOD))
                .isInstanceOf(RestClientException.class);
    }

    @Test
    void classify_malformedJson_throwsException() {
        stubFor(post(urlEqualTo("/classify/layoutlm"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{not valid json")));

        assertThatThrownBy(() -> service.classify(testFilePath, DocumentQuality.GOOD))
                .isInstanceOf(RestClientException.class);
    }

    @Test
    void classify_sendsMultipartRequest() {
        stubFor(post(urlEqualTo("/classify/layoutlm"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"document_type\":\"memo\",\"confidence\":0.8,\"model\":\"layoutlmv3\",\"details\":{}}")));

        service.classify(testFilePath, DocumentQuality.GOOD);

        wireMockServer.verify(postRequestedFor(urlEqualTo("/classify/layoutlm"))
                .withHeader("Content-Type", containing("multipart/form-data")));
    }
}
