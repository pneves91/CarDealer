package com.cardealer.services.security;

import com.cardealer.repositories.TokenRepository;
import com.cardealer.repositories.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No JWT token provided in Authorization header");
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        String email;

        try {
            email = jwtService.extractEmail(jwt);
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
            handleError(response, HttpServletResponse.SC_UNAUTHORIZED, "JWT token has expired");
            return;
        } catch (MalformedJwtException | SignatureException | IllegalArgumentException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            handleError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or malformed JWT token");
            return;
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            var user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                log.warn("Token is valid but user not found: {}", email);
                handleError(response, HttpServletResponse.SC_NOT_FOUND, "User not found with email: " + email);
                return;
            }

            var tokenRecord = tokenRepository.findByToken(jwt).orElse(null);

            boolean isValid = tokenRecord != null
                    && !tokenRecord.isExpired()
                    && !tokenRecord.isRevoked()
                    && jwtService.isTokenValid(jwt, user.getEmail());

            if (!isValid) {
                log.warn("Invalid or revoked token for user: {}", email);
                handleError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or revoked token");
                return;
            }

            var authToken = new UsernamePasswordAuthenticationToken(
                    user.getEmail(),
                    null,
                    user.getRole().getAuthorities()
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);

            log.debug("Authentication successful for user: {}", user.getEmail());
        }

        filterChain.doFilter(request, response);
    }

    private void handleError(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        String body = String.format("""
            {
                "timestamp": "%s",
                "status": %d,
                "error": "%s",
                "message": "%s",
                "fieldErrors": null
            }
            """, LocalDateTime.now(), statusCode, HttpStatus.valueOf(statusCode).getReasonPhrase(), message);
        response.getWriter().write(body);
    }
}
