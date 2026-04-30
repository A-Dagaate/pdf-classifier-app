package com.example.pdfclassifier.security;

import com.example.pdfclassifier.entity.User;
import com.example.pdfclassifier.repository.UserRepository;
import com.example.pdfclassifier.util.TestDataFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwoFactorAuthenticationFilterTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private TwoFactorAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    // ── Passthrough cases ───────────────────────────────────────

    @Test
    void unauthenticated_passesThrough() throws Exception {
        request.setRequestURI("/dashboard");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void anonymousUser_passesThrough() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, Collections.emptyList()));
        request.setRequestURI("/dashboard");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void authenticatedUser_no2FA_passesThrough() throws Exception {
        setAuthenticated("testuser");
        User user = TestDataFactory.createUser();
        user.set2faEnabled(false);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        request.setRequestURI("/dashboard");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void authenticatedUser_userNotFound_passesThrough() throws Exception {
        setAuthenticated("ghost");
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        request.setRequestURI("/dashboard");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // ── 2FA redirect cases ──────────────────────────────────────

    @Test
    void authenticatedUser_2faEnabled_notVerified_redirectsToVerify() throws Exception {
        setAuthenticated("testuser");
        User user = TestDataFactory.createUser();
        user.set2faEnabled(true);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        request.setRequestURI("/dashboard");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getRedirectedUrl()).isEqualTo("/verify-2fa");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void authenticatedUser_2faEnabled_noSession_redirects() throws Exception {
        setAuthenticated("testuser");
        User user = TestDataFactory.createUser();
        user.set2faEnabled(true);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        request.setRequestURI("/settings");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getRedirectedUrl()).isEqualTo("/verify-2fa");
    }

    @Test
    void authenticatedUser_2faEnabled_verified_passesThrough() throws Exception {
        setAuthenticated("testuser");
        User user = TestDataFactory.createUser();
        user.set2faEnabled(true);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        request.setRequestURI("/dashboard");
        HttpSession session = request.getSession(true);
        session.setAttribute("2FA_VERIFIED", true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void authenticatedUser_2faEnabled_requestingVerifyPage_passesThrough() throws Exception {
        setAuthenticated("testuser");
        User user = TestDataFactory.createUser();
        user.set2faEnabled(true);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        request.setRequestURI("/verify-2fa");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void authenticatedUser_2faEnabled_requestingPerform2fa_passesThrough() throws Exception {
        setAuthenticated("testuser");
        User user = TestDataFactory.createUser();
        user.set2faEnabled(true);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        request.setRequestURI("/perform-2fa");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // ── shouldNotFilter ─────────────────────────────────────────

    @Test
    void shouldNotFilter_cssPath() {
        request.setRequestURI("/css/style.css");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_loginPath() {
        request.setRequestURI("/login");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_registerPath() {
        request.setRequestURI("/register");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_dashboardPath_returnsFalse() {
        request.setRequestURI("/dashboard");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    private void setAuthenticated(String username) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(username, "pass", Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
