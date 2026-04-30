package com.example.pdfclassifier.repository;

import com.example.pdfclassifier.entity.PdfDocument;
import com.example.pdfclassifier.entity.PdfDocument.ProcessingStatus;
import com.example.pdfclassifier.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(PdfDocumentRepositoryTest.TestConfig.class)
class PdfDocumentRepositoryTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PdfDocumentRepository pdfDocumentRepository;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        pdfDocumentRepository.deleteAll();

        user1 = new User();
        user1.setUsername("user1");
        user1.setEmail("user1@test.com");
        user1.setPassword("pw");
        entityManager.persistAndFlush(user1);

        user2 = new User();
        user2.setUsername("user2");
        user2.setEmail("user2@test.com");
        user2.setPassword("pw");
        entityManager.persistAndFlush(user2);

        createDocument(user1, "doc1.pdf", ProcessingStatus.PENDING);
        createDocument(user1, "doc2.pdf", ProcessingStatus.COMPLETED);
        createDocument(user2, "doc3.pdf", ProcessingStatus.PENDING);
    }

    private void createDocument(User user, String filename, ProcessingStatus status) {
        PdfDocument doc = new PdfDocument();
        doc.setOriginalFilename(filename);
        doc.setStoredFilename("uuid_" + filename);
        doc.setFilePath("/uploads/uuid_" + filename);
        doc.setFileSize(1024L);
        doc.setUser(user);
        doc.setProcessingStatus(status);
        entityManager.persistAndFlush(doc);
    }

    @Test
    void findByUser_returnsUserDocuments() {
        List<PdfDocument> docs = pdfDocumentRepository.findByUser(user1);

        assertThat(docs).hasSize(2);
        assertThat(docs).allSatisfy(d -> assertThat(d.getUser().getId()).isEqualTo(user1.getId()));
    }

    @Test
    void findByUser_noDocuments_returnsEmpty() {
        User user3 = new User();
        user3.setUsername("user3");
        user3.setEmail("user3@test.com");
        user3.setPassword("pw");
        entityManager.persistAndFlush(user3);

        List<PdfDocument> docs = pdfDocumentRepository.findByUser(user3);

        assertThat(docs).isEmpty();
    }

    @Test
    void findByProcessingStatus_returnsMatchingDocs() {
        List<PdfDocument> pending = pdfDocumentRepository.findByProcessingStatus(ProcessingStatus.PENDING);

        assertThat(pending).hasSize(2);
    }

    @Test
    void findByProcessingStatus_noMatch_returnsEmpty() {
        List<PdfDocument> failed = pdfDocumentRepository.findByProcessingStatus(ProcessingStatus.FAILED);

        assertThat(failed).isEmpty();
    }

    @Test
    void findByUserAndProcessingStatus_returnsFilteredDocs() {
        List<PdfDocument> result = pdfDocumentRepository.findByUserAndProcessingStatus(user1, ProcessingStatus.COMPLETED);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOriginalFilename()).isEqualTo("doc2.pdf");
    }

    @Test
    void prePersist_setsUploadDate() {
        List<PdfDocument> docs = pdfDocumentRepository.findByUser(user1);

        assertThat(docs).allSatisfy(d -> assertThat(d.getUploadDate()).isNotNull());
    }
}
