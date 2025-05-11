package com.cardealer.services.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    private Key key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String extractEmail(String token) {
        // Extrai o email (subject) do JWT
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, String expectedEmail) {
        // Verifica se o token é válido: subject coincide e ainda não expirou
        try {
            final String email = extractEmail(token);
            boolean valid = email.equals(expectedEmail) && !isTokenExpired(token);
            if (!valid) {
                log.debug("Token is invalid or expired for email: {}", expectedEmail);
            }
            return valid;
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        // Verifica se o token já expirou com base na data de expiração
        return extractExpiration(token).before(new Date());
    }

    public Date extractExpiration(String token) {
        // Extrai a data de expiração do token
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        // Extrai uma claim genérica do JWT com base numa função
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        // Extrai todas as claims do token JWT usando a chave privada
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String generateAccessToken(String email, Map<String, Object> extraClaims) {
        // Gera um access token com as claims extra e expiração configurada
        return buildToken(email, extraClaims, jwtExpiration);
    }

    public String generateRefreshToken(String email, Map<String, Object> extraClaims) {
        // Gera um refresh token com expiração maior que o access token
        return buildToken(email, extraClaims, jwtExpiration * 5);
    }

    private String buildToken(String email, Map<String, Object> extraClaims, long expirationMillis) {
        // Constrói um JWT com subject, claims, timestamps e assinatura
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Key getKey() {
        // Retorna a chave usada para assinar/verificar os tokens JWT
        return this.key;
    }
}
