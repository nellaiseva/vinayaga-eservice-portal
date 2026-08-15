package com.eservice1.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;

import com.eservice1.user.entity.User;
import com.eservice1.user.repository.UserRepository;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtService jwtService;

    private final UserRepository userRepository;

    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    public JwtFilter(
            JwtService jwtService,
            UserRepository userRepository,
            JwtAuthenticationEntryPoint authenticationEntryPoint) {

        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getServletPath();

        for (String publicPattern : SecurityConfig.PUBLIC_URL_PATTERNS) {
            if (PATH_MATCHER.match(publicPattern, path)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void doFilterInternal(

            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        Authentication existingAuthentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (existingAuthentication != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();

        if (token.isBlank() || !jwtService.isValid(token)) {
            sendUnauthorized(request, response);
            return;
        }

        try {
            String phoneNumber = jwtService.extractPhoneNumber(token);

            if (phoneNumber == null || phoneNumber.isBlank()) {
                sendUnauthorized(request, response);
                return;
            }

            User user = userRepository.findByPhoneNumber(phoneNumber)
                    .orElse(null);

            if (user == null || user.getRole() == null) {
                sendUnauthorized(request, response);
                return;
            }

            UserDetails principal = new org.springframework.security.core.userdetails.User(
                    phoneNumber,
                    "",
                    java.util.List.of(user.getAuthority())
            );

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities()
                    );

            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException exception) {
            sendUnauthorized(request, response);
            return;
        }

        filterChain.doFilter(
                request,
                response
        );
    }

    private void sendUnauthorized(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException, ServletException {

        SecurityContextHolder.clearContext();

        authenticationEntryPoint.commence(
                request,
                response,
                new InsufficientAuthenticationException(
                        "Invalid or expired authentication token"
                )
        );
    }
}
