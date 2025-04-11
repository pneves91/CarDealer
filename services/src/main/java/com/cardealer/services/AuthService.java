package com.cardealer.services;

import com.cardealer.models.Role;
import com.cardealer.models.Token;
import com.cardealer.models.User;
import com.cardealer.models.request.auth.LoginRequest;
import com.cardealer.models.request.auth.RegisterRequest;
import com.cardealer.models.response.auth.AuthTokensResponse;
import com.cardealer.repositories.TokenRepository;
import com.cardealer.repositories.UserRepository;
import com.cardealer.services.exceptions.EmailAlreadyExistsException;
import com.cardealer.services.security.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthTokensResponse register(RegisterRequest registerRequest) {
        var user = User.builder()
                .name(registerRequest.getName())
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .role(Role.USER)
                .build();

        if (userRepository.findByEmail(registerRequest.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException(registerRequest.getEmail());
        }

        userRepository.save(user);

        var accessToken = jwtService.generateAccessToken(user.getEmail(), new HashMap<>());
        var refreshToken = jwtService.generateRefreshToken(user.getEmail(), new HashMap<>());

        saveUserToken(user, accessToken);

        return new AuthTokensResponse(accessToken, refreshToken);
    }

    public AuthTokensResponse login(LoginRequest loginRequest) {
        var authToken = new UsernamePasswordAuthenticationToken(
                loginRequest.getEmail(),
                loginRequest.getPassword()
        );

        authenticationManager.authenticate(authToken);

        var user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow();

        var accessToken = jwtService.generateAccessToken(user.getEmail(), new HashMap<>());
        var refreshToken = jwtService.generateRefreshToken(user.getEmail(), new HashMap<>());

        revokeAllUserTokens(user);
        saveUserToken(user, accessToken);

        return new AuthTokensResponse(accessToken, refreshToken);
    }

    private void saveUserToken(User user, String jwtToken) {
        var token = Token.builder()
                .user(user)
                .token(jwtToken)
                .expired(false)
                .revoked(false)
                .build();

        tokenRepository.save(token);
    }

    private void revokeAllUserTokens(User user) {
        List<Token> tokens = tokenRepository.findAllByUser(user);
        if (tokens.isEmpty()) return;

        tokens.forEach(t -> {
            t.setRevoked(true);
            t.setExpired(true);
        });

        tokenRepository.saveAll(tokens);
    }

    public void logout(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        tokenRepository.findByToken(token).ifPresent(t -> {
            t.setRevoked(true);
            t.setExpired(true);
            tokenRepository.save(t);
        });
    }

    public AuthTokensResponse refreshToken(String accessToken, String refreshToken) {
        String email = jwtService.extractEmail(refreshToken);

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!jwtService.isTokenValid(refreshToken, user.getEmail()))
            throw new RuntimeException("Invalid refresh token");

        String newAccessToken = jwtService.generateAccessToken(user.getEmail(), new HashMap<>());

        revokeAllUserTokens(user);
        saveUserToken(user, newAccessToken);

        return new AuthTokensResponse(newAccessToken, refreshToken);
    }

    public User getUserProfile(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        String email = jwtService.extractEmail(token);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public void forgotPassword(String email) {
        // TO DO: gerar token de recuperação, guardar ou associar ao utilizador
        // TO DO: enviar email com link ou código
        System.out.println("Simular envio de email para " + email);
    }

    public void resetPassword(String token, String newPassword) {
        // TO DO: verificar token recebido por email
        // TO DO: atualizar password do utilizador
        System.out.println("Simular reset com token: " + token + " -> nova password: " + newPassword);
    }

    public void verifyEmail(String token) {
        // TO DO: validar token e ativar conta
        System.out.println("Simular verificação de email com token: " + token);
    }

    public void resendVerificationEmail(String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // TO DO: gerar novo token de verificação (UUID ou JWT curto com claims de email)
        // TO DO: guardar token num repositório ou enviar diretamente no link
        // TO DO: simular envio de email

        String mockToken = "mock-verification-token-123456";
        String verificationLink = "https://teu-dominio.com/auth/verify-email?token=" + mockToken;

        System.out.printf("Simular reenvio de email de verificação para %s com link: %s%n",
                user.getEmail(), verificationLink);
    }

    private String extractToken(String header) {
        if (header == null || !header.startsWith("Bearer "))
            throw new RuntimeException("Token inválido");
        return header.substring(7);
    }
}
