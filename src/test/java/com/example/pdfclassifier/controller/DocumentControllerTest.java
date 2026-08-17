package com.example.pdfclassifier.controller;

import com.example.pdfclassifier.config.SecurityConfig;
import com.example.pdfclassifier.entity.PdfDocument;
import com.example.pdfclassifier.entity.PdfDocumentContent;
import com.example.pdfclassifier.entity.User;
import com.example.pdfclassifier.repository.PdfDocumentRepository;
import com.example.pdfclassifier.repository.UserRepository;
import com.example.pdfclassifier.security.TwoFactorAuthenticationFilter;
import com.example.pdfclassifier.service.DocumentContentService;
import com.example.pdfclassifier.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
@ActiveProfiles("test")
@Import({DocumentControllerTest.TestConfig.class, SecurityConfig.class})
class DocumentControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        TwoFactorAuthenticationFilter twoFactorAuthenticationFilter(UserRepository userRepository) {
            return new TwoFactorAuthenticationFilter(userRepository);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PdfDocumentRepository pdfDocumentRepository;

    @MockBean
    private DocumentContentService documentContentService;

    @MockBean
    private UserService userService;

    @MockBean
    private UserRepository userRepository;

    /** Required by SecurityConfig, which is imported to exercise the real filter chain. */
    @MockBean
    private com.example.pdfclassifier.security.CustomUserDetailsService customUserDetailsService;

    private User owner;
    private User intruder;
    private PdfDocument document;
    private PdfDocumentContent content;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setUsername("owner");

        intruder = new User();
        intruder.setId(2L);
        intruder.setUsername("intruder");

        document = new PdfDocument();
        document.setId(42L);
        document.setUser(owner);
        document.setOriginalFilename("invoice.pdf");
        document.setUploadDate(LocalDateTime.now());
        document.setProcessingStatus(PdfDocument.ProcessingStatus.COMPLETED);
        document.setDocumentQuality(PdfDocument.DocumentQuality.GOOD);
        document.setClassificationResult("Document Type: INVOICE");

        content = new PdfDocumentContent();
        content.setDocumentId(42L);
        content.setUserId(1L);
        content.setPdfData(new byte[]{'%', 'P', 'D', 'F'});
        content.setThumbnailData(new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        content.setContentType("application/pdf");

        when(pdfDocumentRepository.findById(42L)).thenReturn(Optional.of(document));
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(userRepository.findByUsername("intruder")).thenReturn(Optional.of(intruder));
    }

    @Test
    @WithMockUser(username = "owner")
    void viewDocument_ownerSeesPage() throws Exception {
        when(userService.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentContentService.findContent(42L)).thenReturn(Optional.of(content));

        mockMvc.perform(get("/documents/42"))
                .andExpect(status().isOk())
                .andExpect(view().name("document"))
                .andExpect(model().attribute("hasPreview", true));
    }

    @Test
    @WithMockUser(username = "intruder")
    void viewDocument_otherUsersDocument_is404NotForbidden() throws Exception {
        // 404 rather than 403 on purpose: 403 would confirm the id exists,
        // letting anyone enumerate how many documents the system holds.
        when(userService.findByUsername("intruder")).thenReturn(Optional.of(intruder));

        mockMvc.perform(get("/documents/42"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "intruder")
    void serveFile_otherUsersDocument_is404() throws Exception {
        when(userService.findByUsername("intruder")).thenReturn(Optional.of(intruder));

        mockMvc.perform(get("/documents/42/file"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "intruder")
    void serveThumbnail_otherUsersDocument_is404() throws Exception {
        when(userService.findByUsername("intruder")).thenReturn(Optional.of(intruder));

        mockMvc.perform(get("/documents/42/thumbnail"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "owner")
    void serveFile_returnsPdfInlineWithSandbox() throws Exception {
        when(userService.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentContentService.findContent(42L)).thenReturn(Optional.of(content));

        mockMvc.perform(get("/documents/42/file"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "inline; filename=\"invoice.pdf\""))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @WithMockUser(username = "owner")
    void serveThumbnail_returnsPng() throws Exception {
        when(userService.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentContentService.findContent(42L)).thenReturn(Optional.of(content));

        mockMvc.perform(get("/documents/42/thumbnail"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    @WithMockUser(username = "owner")
    void serveThumbnail_noStoredContent_is404() throws Exception {
        when(userService.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentContentService.findContent(42L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/documents/42/thumbnail"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "owner")
    void viewDocument_prunedContent_stillRendersWithoutPreview() throws Exception {
        when(userService.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentContentService.findContent(42L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/documents/42"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasPreview", false));
    }

    @Test
    void viewDocument_anonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/documents/42"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "owner")
    void viewDocument_unknownId_is404() throws Exception {
        when(userService.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(pdfDocumentRepository.findById(anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(get("/documents/999"))
                .andExpect(status().isNotFound());
    }
}
