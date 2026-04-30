package com.example.pdfclassifier;

import com.example.pdfclassifier.config.TestAsyncConfig;
import com.example.pdfclassifier.entity.User;
import com.example.pdfclassifier.repository.PdfDocumentRepository;
import com.example.pdfclassifier.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestAsyncConfig.class)
class PdfClassifierApplicationE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PdfDocumentRepository pdfDocumentRepository;

    @MockBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        pdfDocumentRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        pdfDocumentRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void contextLoads() {
        assertThat(port).isPositive();
    }

    @Test
    void register_createsUser() {
        // Get the registration page to obtain CSRF token
        ResponseEntity<String> registerPage = restTemplate.getForEntity("/register", String.class);
        assertThat(registerPage.getStatusCode()).isEqualTo(HttpStatus.OK);

        String csrfToken = extractCsrfToken(registerPage.getBody());
        String sessionCookie = extractSessionCookie(registerPage.getHeaders());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add("Cookie", sessionCookie);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("username", "e2euser");
        body.add("email", "e2e@test.com");
        body.add("password", "password123");
        body.add("confirmPassword", "password123");
        body.add("_csrf", csrfToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity("/register", request, String.class);

        // Should redirect to login
        assertThat(response.getStatusCode()).isIn(HttpStatus.FOUND, HttpStatus.SEE_OTHER);

        // User should exist in DB
        assertThat(userRepository.existsByUsername("e2euser")).isTrue();
    }

    @Test
    void loginPage_isAccessible() {
        ResponseEntity<String> response = restTemplate.getForEntity("/login", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("login");
    }

    private String extractCsrfToken(String html) {
        if (html == null) return "";
        Pattern pattern = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        pattern = Pattern.compile("value=\"([^\"]+)\"[^>]*name=\"_csrf\"");
        matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String extractSessionCookie(HttpHeaders headers) {
        List<String> cookies = headers.get("Set-Cookie");
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.contains("JSESSIONID")) {
                    return cookie.split(";")[0];
                }
            }
        }
        return "";
    }
}
