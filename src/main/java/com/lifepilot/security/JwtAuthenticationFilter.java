package com.lifepilot.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String header = request.getHeader("Authorization");
        boolean bearerPresented = header != null && header.startsWith("Bearer ");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                UUID userId = jwtService.readUserId(header.substring(7));
                var authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, java.util.List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
                logger.warn("Rejected invalid JWT for {} {}", request.getMethod(), request.getRequestURI());
            }
        }
        filterChain.doFilter(request, response);
        logger.info("{} {} authenticated={} bearerPresented={} status={} durationMs={}",
                request.getMethod(), request.getRequestURI(),
                SecurityContextHolder.getContext().getAuthentication() != null,
                bearerPresented, response.getStatus(),
                (System.nanoTime() - startedAt) / 1_000_000);
    }
}
