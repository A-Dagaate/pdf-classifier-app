package com.example.pdfclassifier.security;

import com.example.pdfclassifier.entity.User;
import com.example.pdfclassifier.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class TwoFactorAuthenticationFilter extends OncePerRequestFilter {
    
    private final UserRepository userRepository;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated() 
                && !authentication.getPrincipal().equals("anonymousUser")) {
            
            String username = authentication.getName();
            User user = userRepository.findByUsername(username).orElse(null);
            
            if (user != null && user.is2faEnabled()) {
                HttpSession session = request.getSession(false);
                
                // Check if 2FA has been verified in this session
                if (session == null || session.getAttribute("2FA_VERIFIED") == null) {
                    // If requesting 2FA verification page, allow it
                    if (!request.getRequestURI().contains("/verify-2fa") 
                            && !request.getRequestURI().contains("/perform-2fa")) {
                        response.sendRedirect(request.getContextPath() + "/verify-2fa");
                        return;
                    }
                }
            }
        }
        
        filterChain.doFilter(request, response);
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.contains("/css") || path.contains("/js") || 
               path.contains("/images") || path.contains("/login") ||
               path.contains("/register") || path.equals("/");
    }
}
