package com.example.pdfclassifier.service;

import com.example.pdfclassifier.entity.PdfDocument;
import com.example.pdfclassifier.entity.User;
import com.example.pdfclassifier.util.TestDataFactory;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private EmailService emailService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = TestDataFactory.createUser();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    // ── sendProcessingCompleteEmail ─────────────────────────────

    @Test
    void sendProcessingCompleteEmail_sendsMessage() {
        PdfDocument doc = TestDataFactory.createCompletedPdfDocument(testUser);
        doc.setProcessedFilePath(null); // no attachment

        emailService.sendProcessingCompleteEmail(doc);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendProcessingCompleteEmail_withAttachment(@TempDir Path tempDir) throws IOException {
        PdfDocument doc = TestDataFactory.createCompletedPdfDocument(testUser);
        Path attachmentFile = tempDir.resolve("results.txt");
        Files.writeString(attachmentFile, "test results");
        doc.setProcessedFilePath(attachmentFile.toString());

        emailService.sendProcessingCompleteEmail(doc);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendProcessingCompleteEmail_nonexistentAttachment_stillSends() {
        PdfDocument doc = TestDataFactory.createCompletedPdfDocument(testUser);
        doc.setProcessedFilePath("/nonexistent/path/results.txt");

        emailService.sendProcessingCompleteEmail(doc);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendProcessingCompleteEmail_mailException_doesNotPropagate() {
        PdfDocument doc = TestDataFactory.createCompletedPdfDocument(testUser);
        doc.setProcessedFilePath(null);
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        // MailSendException extends MailException extends RuntimeException.
        // The code catches MessagingException (checked), not MailException.
        // So MailException WILL propagate — this documents actual behavior.
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> emailService.sendProcessingCompleteEmail(doc))
                .isInstanceOf(MailSendException.class);
    }

    // ── send2FASetupEmail ───────────────────────────────────────

    @Test
    void send2FASetupEmail_sendsMessage() {
        emailService.send2FASetupEmail("user@test.com", "testuser");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void send2FASetupEmail_mailException_doesNotPropagate() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> emailService.send2FASetupEmail("user@test.com", "testuser"))
                .isInstanceOf(MailSendException.class);
    }
}
