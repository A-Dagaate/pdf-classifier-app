package com.example.pdfclassifier.controller;

import com.example.pdfclassifier.entity.PdfDocument;
import com.example.pdfclassifier.entity.User;
import com.example.pdfclassifier.service.EmailService;
import com.example.pdfclassifier.service.PdfProcessingService;
import com.example.pdfclassifier.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MainController {
    
    private final UserService userService;
    private final PdfProcessingService pdfProcessingService;
    private final EmailService emailService;
    
    @GetMapping("/")
    public String index() {
        return "redirect:/login";
    }
    
    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                       @RequestParam(required = false) String logout,
                       Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid username or password");
        }
        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully");
        }
        return "login";
    }
    
    @GetMapping("/register")
    public String showRegistrationForm() {
        return "register";
    }
    
    @PostMapping("/register")
    public String registerUser(@RequestParam String username,
                              @RequestParam String email,
                              @RequestParam String password,
                              @RequestParam String confirmPassword,
                              RedirectAttributes redirectAttributes) {
        try {
            if (!password.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "Passwords do not match");
                return "redirect:/register";
            }
            
            userService.registerUser(username, email, password);
            redirectAttributes.addFlashAttribute("message", "Registration successful! Please login.");
            return "redirect:/login";
            
        } catch (Exception e) {
            log.error("Error registering user", e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        }
    }
    
    @GetMapping("/verify-2fa")
    public String show2FAVerification(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        model.addAttribute("username", username);
        return "verify-2fa";
    }
    
    @PostMapping("/perform-2fa")
    public String verify2FA(@RequestParam String code,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            User user = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            if (userService.verify2FACode(user, code)) {
                session.setAttribute("2FA_VERIFIED", true);
                userService.updateLastLogin(user);
                return "redirect:/dashboard";
            } else {
                redirectAttributes.addFlashAttribute("error", "Invalid verification code");
                return "redirect:/verify-2fa";
            }
            
        } catch (Exception e) {
            log.error("Error verifying 2FA code", e);
            redirectAttributes.addFlashAttribute("error", "Verification failed");
            return "redirect:/verify-2fa";
        }
    }
    
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<PdfDocument> documents = pdfProcessingService.getUserDocuments(user);
        
        model.addAttribute("username", username);
        model.addAttribute("user", user);
        model.addAttribute("documents", documents);
        
        return "dashboard";
    }
    
    @PostMapping("/upload")
    public String uploadPdf(@RequestParam("file") MultipartFile file,
                           RedirectAttributes redirectAttributes) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            User user = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Validate PDF file
            if (!pdfProcessingService.validatePdfFile(file)) {
                redirectAttributes.addFlashAttribute("error", 
                    "Invalid file. Please upload a valid PDF file.");
                return "redirect:/dashboard";
            }
            
            // Save file and create database record
            PdfDocument document = pdfProcessingService.saveUploadedFile(file, user);
            
            // Process PDF asynchronously
            pdfProcessingService.processPdfAsync(document.getId());
            
            redirectAttributes.addFlashAttribute("message", 
                "File uploaded successfully! Processing will complete shortly and you will receive an email.");
            
            log.info("PDF uploaded successfully: {} by user: {}", file.getOriginalFilename(), username);
            
        } catch (Exception e) {
            log.error("Error uploading PDF", e);
            redirectAttributes.addFlashAttribute("error", "Error uploading file: " + e.getMessage());
        }
        
        return "redirect:/dashboard";
    }
    
    @GetMapping("/settings")
    public String settings(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        model.addAttribute("user", user);
        
        // If 2FA is enabled, generate QR code
        if (user.is2faEnabled()) {
            String qrCodeDataUri = userService.generate2FAQrCode(user);
            model.addAttribute("qrCode", qrCodeDataUri);
        }
        
        return "settings";
    }
    
    @PostMapping("/enable-2fa")
    public String enable2FA(HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();

            User user = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            userService.enable2FA(user);
            session.setAttribute("2FA_VERIFIED", true);
            emailService.send2FASetupEmail(user.getEmail(), user.getUsername());
            
            redirectAttributes.addFlashAttribute("message", 
                "Two-factor authentication enabled successfully! Scan the QR code with your authenticator app.");
            
        } catch (Exception e) {
            log.error("Error enabling 2FA", e);
            redirectAttributes.addFlashAttribute("error", "Error enabling 2FA: " + e.getMessage());
        }
        
        return "redirect:/settings";
    }
    
    @PostMapping("/disable-2fa")
    public String disable2FA(RedirectAttributes redirectAttributes) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            User user = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            userService.disable2FA(user);
            
            redirectAttributes.addFlashAttribute("message", 
                "Two-factor authentication disabled successfully!");
            
        } catch (Exception e) {
            log.error("Error disabling 2FA", e);
            redirectAttributes.addFlashAttribute("error", "Error disabling 2FA: " + e.getMessage());
        }
        
        return "redirect:/settings";
    }
}
