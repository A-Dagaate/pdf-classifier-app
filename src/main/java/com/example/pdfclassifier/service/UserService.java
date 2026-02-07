package com.example.pdfclassifier.service;

import com.example.pdfclassifier.entity.User;
import com.example.pdfclassifier.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    
    @Transactional
    public User registerUser(String username, String email, String password) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already exists");
        }
        
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists");
        }
        
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.set2faEnabled(false);
        
        return userRepository.save(user);
    }
    
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    @Transactional
    public void enable2FA(User user) {
        String secret = totpService.generateSecret();
        user.setTotpSecret(secret);
        user.set2faEnabled(true);
        userRepository.save(user);
    }
    
    @Transactional
    public void disable2FA(User user) {
        user.setTotpSecret(null);
        user.set2faEnabled(false);
        userRepository.save(user);
    }
    
    public String generate2FAQrCode(User user) {
        if (user.getTotpSecret() == null) {
            throw new RuntimeException("User does not have a TOTP secret");
        }
        return totpService.generateQrCodeDataUri(user.getTotpSecret(), user.getUsername());
    }
    
    public boolean verify2FACode(User user, String code) {
        if (user.getTotpSecret() == null) {
            return false;
        }
        return totpService.verifyCode(user.getTotpSecret(), code);
    }
    
    @Transactional
    public void updateLastLogin(User user) {
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
    }
}
