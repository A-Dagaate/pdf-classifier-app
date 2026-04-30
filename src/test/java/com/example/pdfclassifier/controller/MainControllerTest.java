package com.example.pdfclassifier.controller;

import com.example.pdfclassifier.config.SecurityConfig;
import com.example.pdfclassifier.entity.PdfDocument;
import com.example.pdfclassifier.entity.User;
import com.example.pdfclassifier.repository.UserRepository;
import com.example.pdfclassifier.security.CustomUserDetailsService;
import com.example.pdfclassifier.security.TwoFactorAuthenticationFilter;
import com.example.pdfclassifier.service.EmailService;
import com.example.pdfclassifier.service.PdfProcessingService;
import com.example.pdfclassifier.service.UserService;
import com.example.pdfclassifier.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MainController.class)
@ActiveProfiles("test")
@Import({MainControllerTest.TestConfig.class, SecurityConfig.class})
class MainControllerTest {

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
    private UserService userService;

    @MockBean
    private PdfProcessingService pdfProcessingService;

    @MockBean
    private EmailService emailService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = TestDataFactory.createUser();
    }

    // ── Public endpoints ────────────────────────────────────────

    @Test
    void index_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginPage_returnsLoginView() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void loginPage_withError_addsErrorAttribute() throws Exception {
        mockMvc.perform(get("/login").param("error", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void loginPage_withLogout_addsMessageAttribute() throws Exception {
        mockMvc.perform(get("/login").param("logout", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attributeExists("message"));
    }

    @Test
    void registerPage_returnsRegisterView() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));
    }

    // ── Registration ────────────────────────────────────────────

    @Test
    void register_success_redirectsToLogin() throws Exception {
        when(userService.registerUser("newuser", "new@test.com", "pass123"))
                .thenReturn(testUser);

        mockMvc.perform(post("/register")
                        .param("username", "newuser")
                        .param("email", "new@test.com")
                        .param("password", "pass123")
                        .param("confirmPassword", "pass123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("message"));
    }

    @Test
    void register_passwordMismatch_redirectsBack() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "newuser")
                        .param("email", "new@test.com")
                        .param("password", "pass123")
                        .param("confirmPassword", "different")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void register_duplicateUsername_redirectsBack() throws Exception {
        when(userService.registerUser("existing", "e@test.com", "pass"))
                .thenThrow(new RuntimeException("Username already exists"));

        mockMvc.perform(post("/register")
                        .param("username", "existing")
                        .param("email", "e@test.com")
                        .param("password", "pass")
                        .param("confirmPassword", "pass")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"))
                .andExpect(flash().attributeExists("error"));
    }

    // ── Protected endpoints require auth ────────────────────────

    @Test
    void dashboard_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void settings_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/settings"))
                .andExpect(status().is3xxRedirection());
    }

    // ── Dashboard ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = "testuser")
    void dashboard_authenticated_returnsView() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(pdfProcessingService.getUserDocuments(testUser)).thenReturn(List.of());

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("username", "user", "documents"));
    }

    // ── Upload ──────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "testuser")
    void upload_validPdf_redirectsWithSuccess() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(pdfProcessingService.validatePdfFile(any())).thenReturn(true);
        PdfDocument doc = TestDataFactory.createPdfDocument(testUser);
        when(pdfProcessingService.saveUploadedFile(any(), eq(testUser), any())).thenReturn(doc);

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "pdf data".getBytes());

        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .param("quality", "GOOD")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attributeExists("message"));

        verify(pdfProcessingService).processPdfAsync(doc.getId());
    }

    @Test
    @WithMockUser(username = "testuser")
    void upload_invalidPdf_redirectsWithError() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(pdfProcessingService.validatePdfFile(any())).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile("file", "bad.txt", "text/plain", "not pdf".getBytes());

        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .param("quality", "GOOD")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void upload_poorQuality_setsQuality() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(pdfProcessingService.validatePdfFile(any())).thenReturn(true);
        PdfDocument doc = TestDataFactory.createPdfDocument(testUser);
        when(pdfProcessingService.saveUploadedFile(any(), eq(testUser), eq(PdfDocument.DocumentQuality.POOR))).thenReturn(doc);

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "pdf data".getBytes());

        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .param("quality", "POOR")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void upload_noCsrf_forbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "pdf data".getBytes());

        mockMvc.perform(multipart("/upload")
                        .file(file)
                        .param("quality", "GOOD"))
                .andExpect(status().isForbidden());
    }

    // ── Settings ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "testuser")
    void settings_authenticated_returnsView() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("settings"))
                .andExpect(model().attributeExists("user"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void settings_2faEnabled_includesQrCode() throws Exception {
        testUser.set2faEnabled(true);
        testUser.setTotpSecret("SECRET");
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userService.generate2FAQrCode(testUser)).thenReturn("data:image/png;base64,qrdata");

        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("qrCode"));
    }

    // ── 2FA enable/disable ──────────────────────────────────────

    @Test
    @WithMockUser(username = "testuser")
    void enable2FA_redirectsToSettings() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        mockMvc.perform(post("/enable-2fa").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("message"));

        verify(userService).enable2FA(testUser);
        verify(emailService).send2FASetupEmail(testUser.getEmail(), testUser.getUsername());
    }

    @Test
    @WithMockUser(username = "testuser")
    void disable2FA_redirectsToSettings() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        mockMvc.perform(post("/disable-2fa").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("message"));

        verify(userService).disable2FA(testUser);
    }

    // ── 2FA verification ────────────────────────────────────────

    @Test
    @WithMockUser(username = "testuser")
    void verify2fa_showsPage() throws Exception {
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        mockMvc.perform(get("/verify-2fa"))
                .andExpect(status().isOk())
                .andExpect(view().name("verify-2fa"))
                .andExpect(model().attributeExists("username"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void perform2fa_validCode_redirectsToDashboard() throws Exception {
        testUser.setTotpSecret("SECRET");
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userService.verify2FACode(testUser, "123456")).thenReturn(true);

        mockMvc.perform(post("/perform-2fa")
                        .param("code", "123456")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void perform2fa_invalidCode_redirectsBack() throws Exception {
        testUser.setTotpSecret("SECRET");
        when(userService.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userService.verify2FACode(testUser, "000000")).thenReturn(false);

        mockMvc.perform(post("/perform-2fa")
                        .param("code", "000000")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verify-2fa"))
                .andExpect(flash().attributeExists("error"));
    }
}
