package com.example.pdfclassifier.service;

import com.example.pdfclassifier.entity.User;
import com.example.pdfclassifier.repository.UserRepository;
import com.example.pdfclassifier.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TotpService totpService;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = TestDataFactory.createUser();
    }

    // ── registerUser ────────────────────────────────────────────

    @Test
    void registerUser_success() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.registerUser("newuser", "new@test.com", "password");

        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getEmail()).isEqualTo("new@test.com");
        assertThat(result.getPassword()).isEqualTo("encoded");
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.is2faEnabled()).isFalse();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerUser_duplicateUsername_throws() {
        when(userRepository.existsByUsername("existing")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerUser("existing", "e@test.com", "pw"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Username already exists");
    }

    @Test
    void registerUser_duplicateEmail_throws() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerUser("newuser", "dup@test.com", "pw"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email already exists");
    }

    // ── findByUsername ──────────────────────────────────────────

    @Test
    void findByUsername_found() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        Optional<User> result = userService.findByUsername("testuser");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    void findByUsername_notFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        Optional<User> result = userService.findByUsername("ghost");

        assertThat(result).isEmpty();
    }

    // ── 2FA ─────────────────────────────────────────────────────

    @Test
    void enable2FA_setsSecretAndFlag() {
        when(totpService.generateSecret()).thenReturn("SECRET123");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.enable2FA(testUser);

        assertThat(testUser.getTotpSecret()).isEqualTo("SECRET123");
        assertThat(testUser.is2faEnabled()).isTrue();
        verify(userRepository).save(testUser);
    }

    @Test
    void disable2FA_clearsSecretAndFlag() {
        testUser.setTotpSecret("SECRET123");
        testUser.set2faEnabled(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.disable2FA(testUser);

        assertThat(testUser.getTotpSecret()).isNull();
        assertThat(testUser.is2faEnabled()).isFalse();
        verify(userRepository).save(testUser);
    }

    @Test
    void verify2FACode_delegatesToTotpService() {
        testUser.setTotpSecret("SECRET");
        when(totpService.verifyCode("SECRET", "123456")).thenReturn(true);

        boolean result = userService.verify2FACode(testUser, "123456");

        assertThat(result).isTrue();
    }

    @Test
    void verify2FACode_noSecret_returnsFalse() {
        testUser.setTotpSecret(null);

        boolean result = userService.verify2FACode(testUser, "123456");

        assertThat(result).isFalse();
    }

    @Test
    void generate2FAQrCode_delegatesToTotpService() {
        testUser.setTotpSecret("SECRET");
        when(totpService.generateQrCodeDataUri("SECRET", "testuser")).thenReturn("data:image/png;base64,abc");

        String result = userService.generate2FAQrCode(testUser);

        assertThat(result).isEqualTo("data:image/png;base64,abc");
    }

    @Test
    void generate2FAQrCode_noSecret_throws() {
        testUser.setTotpSecret(null);

        assertThatThrownBy(() -> userService.generate2FAQrCode(testUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("TOTP secret");
    }

    // ── updateLastLogin ─────────────────────────────────────────

    @Test
    void updateLastLogin_setsTimestamp() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateLastLogin(testUser);

        assertThat(testUser.getLastLogin()).isNotNull();
        verify(userRepository).save(testUser);
    }
}
