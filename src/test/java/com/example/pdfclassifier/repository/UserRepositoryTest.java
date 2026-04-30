package com.example.pdfclassifier.repository;

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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import(UserRepositoryTest.TestConfig.class)
class UserRepositoryTest {

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
    private UserRepository userRepository;

    private User savedUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        User user = new User();
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPassword("encodedPassword");
        user.setEnabled(true);
        savedUser = entityManager.persistAndFlush(user);
    }

    @Test
    void findByUsername_found() {
        Optional<User> result = userRepository.findByUsername("testuser");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    void findByUsername_notFound() {
        Optional<User> result = userRepository.findByUsername("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void findByEmail_found() {
        Optional<User> result = userRepository.findByEmail("test@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void findByEmail_notFound() {
        Optional<User> result = userRepository.findByEmail("nobody@test.com");

        assertThat(result).isEmpty();
    }

    @Test
    void existsByUsername_true() {
        assertThat(userRepository.existsByUsername("testuser")).isTrue();
    }

    @Test
    void existsByUsername_false() {
        assertThat(userRepository.existsByUsername("ghost")).isFalse();
    }

    @Test
    void existsByEmail_true() {
        assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
    }

    @Test
    void existsByEmail_false() {
        assertThat(userRepository.existsByEmail("nobody@test.com")).isFalse();
    }

    @Test
    void uniqueUsername_constraint() {
        User duplicate = new User();
        duplicate.setUsername("testuser");
        duplicate.setEmail("other@example.com");
        duplicate.setPassword("pw");

        assertThatThrownBy(() -> {
            entityManager.persistAndFlush(duplicate);
        }).isInstanceOf(Exception.class);
    }

    @Test
    void uniqueEmail_constraint() {
        User duplicate = new User();
        duplicate.setUsername("otheruser");
        duplicate.setEmail("test@example.com");
        duplicate.setPassword("pw");

        assertThatThrownBy(() -> {
            entityManager.persistAndFlush(duplicate);
        }).isInstanceOf(Exception.class);
    }

    @Test
    void prePersist_setsCreatedAt() {
        assertThat(savedUser.getCreatedAt()).isNotNull();
    }

    @Test
    void defaultValues_areSet() {
        assertThat(savedUser.isEnabled()).isTrue();
        assertThat(savedUser.is2faEnabled()).isFalse();
    }
}
