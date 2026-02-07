package com.example.pdfclassifier.service;

import com.example.pdfclassifier.entity.PdfDocument;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    
    private final JavaMailSender mailSender;
    
    @Async
    public void sendProcessingCompleteEmail(PdfDocument document) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            
            helper.setTo(document.getUser().getEmail());
            helper.setSubject("PDF Processing Complete - " + document.getOriginalFilename());
            
            String emailContent = buildEmailContent(document);
            helper.setText(emailContent, true);
            
            // Attach processed file if it exists
            if (document.getProcessedFilePath() != null) {
                File processedFile = new File(document.getProcessedFilePath());
                if (processedFile.exists()) {
                    FileSystemResource file = new FileSystemResource(processedFile);
                    helper.addAttachment("classification_results.txt", file);
                }
            }
            
            mailSender.send(message);
            log.info("Email sent successfully to: {}", document.getUser().getEmail());
            
        } catch (MessagingException e) {
            log.error("Error sending email", e);
        }
    }
    
    private String buildEmailContent(PdfDocument document) {
        StringBuilder content = new StringBuilder();
        content.append("<html><body>");
        content.append("<h2>PDF Processing Complete</h2>");
        content.append("<p>Your PDF document has been successfully processed.</p>");
        content.append("<h3>Document Details:</h3>");
        content.append("<ul>");
        content.append("<li><strong>Filename:</strong> ").append(document.getOriginalFilename()).append("</li>");
        content.append("<li><strong>Upload Date:</strong> ").append(document.getUploadDate()).append("</li>");
        content.append("<li><strong>Processing Date:</strong> ").append(document.getProcessedDate()).append("</li>");
        content.append("<li><strong>Status:</strong> ").append(document.getProcessingStatus()).append("</li>");
        content.append("</ul>");
        
        if (document.getClassificationResult() != null) {
            content.append("<h3>Classification Results:</h3>");
            content.append("<pre>").append(document.getClassificationResult()).append("</pre>");
        }
        
        content.append("<p>The processed file is attached to this email.</p>");
        content.append("<p>Thank you for using our PDF Classification Service!</p>");
        content.append("</body></html>");
        
        return content.toString();
    }
    
    @Async
    public void send2FASetupEmail(String email, String username) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            
            helper.setTo(email);
            helper.setSubject("Two-Factor Authentication Enabled");
            
            String emailContent = build2FAEmailContent(username);
            helper.setText(emailContent, true);
            
            mailSender.send(message);
            log.info("2FA setup email sent to: {}", email);
            
        } catch (MessagingException e) {
            log.error("Error sending 2FA setup email", e);
        }
    }
    
    private String build2FAEmailContent(String username) {
        return "<html><body>" +
                "<h2>Two-Factor Authentication Enabled</h2>" +
                "<p>Hello " + username + ",</p>" +
                "<p>Two-factor authentication has been successfully enabled on your account.</p>" +
                "<p>You will now need to enter a verification code from your authenticator app when logging in.</p>" +
                "<p>If you did not enable this feature, please contact support immediately.</p>" +
                "</body></html>";
    }
}
