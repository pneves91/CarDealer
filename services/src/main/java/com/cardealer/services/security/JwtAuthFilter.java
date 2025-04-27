package com.cardealer.services.security;

import com.cardealer.repositories.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("Authorization header missing or does not start with Bearer");
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        String email = null;

        try {
            email = jwtService.extractEmail(jwt);
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token detected");
            handleJwtError(response, "JWT token has expired", HttpStatus.UNAUTHORIZED.value());
            return;
        } catch (JwtException e) {
            log.warn("Invalid or malformed JWT token detected");
            handleJwtError(response, "Invalid or malformed JWT token", HttpStatus.UNAUTHORIZED.value());
            return;
        } catch (Exception e) {
            log.error("Unexpected authentication error", e);
            handleJwtError(response, "Authentication failed", HttpStatus.UNAUTHORIZED.value());
            return;
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            var user = userRepository.findByEmail(email)
                    .orElse(null);

            if (user != null && jwtService.isTokenValid(jwt, user.getEmail())) {
                var authToken = new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        null,
                        user.getRole().getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("Authenticated user set in SecurityContext: {}", user.getEmail());
            } else if (user == null) {
                log.warn("User not found for email extracted from token");
                handleJwtError(response, "User not found", HttpStatus.NOT_FOUND.value());
                return;
            } else {
                log.warn("Token is invalid for user: {}", email);
                handleJwtError(response, "Invalid token", HttpStatus.UNAUTHORIZED.value());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void handleJwtError(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("timestamp", LocalDateTime.now().toString());
        error.put("status", statusCode);
        error.put("error", HttpStatus.valueOf(statusCode).getReasonPhrase());
        error.put("message", message);
        error.put("fieldErrors", null);

        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(error));
    }
}
