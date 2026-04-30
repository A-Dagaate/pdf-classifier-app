package com.example.pdfclassifier.security;

import com.example.pdfclassifier.entity.User;
import com.example.pdfclassifier.repository.UserRepository;
import com.example.pdfclassifier.util.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void loadUserByUsername_found_returnsUserDetails() {
        User user = TestDataFactory.createUser();
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        UserDetails details = customUserDetailsService.loadUserByUsername("testuser");

        assertThat(details.getUsername()).isEqualTo("testuser");
        assertThat(details.getPassword()).isEqualTo(user.getPassword());
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void loadUserByUsername_notFound_throwsUsernameNotFoundException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void loadUserByUsername_disabledUser_returnsDisabledUserDetails() {
        User user = TestDataFactory.createUser();
        user.setEnabled(false);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        UserDetails details = customUserDetailsService.loadUserByUsername("testuser");

        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void loadUserByUsername_hasEmptyAuthorities() {
        User user = TestDataFactory.createUser();
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        UserDetails details = customUserDetailsService.loadUserByUsername("testuser");

        assertThat(details.getAuthorities()).isEmpty();
    }
}
