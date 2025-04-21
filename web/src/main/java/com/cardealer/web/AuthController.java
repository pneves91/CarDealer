package com.cardealer.web;

import com.cardealer.models.User;
import com.cardealer.models.request.auth.*;
import com.cardealer.models.response.auth.AuthTokensResponse;
import com.cardealer.services.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    // Endpoint para registo de novo utilizador e criação de empresa
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest registerRequest) {
        authService.register(registerRequest);
        return ResponseEntity.ok().build();
    }

    // Endpoint para verificação de email
    @GetMapping("/verify-email")
    public ResponseEntity<AuthTokensResponse> verifyEmail(@RequestParam String token) {
        AuthTokensResponse tokens = authService.verifyEmail(token);
        return ResponseEntity.ok(tokens);
    }

    // Endpoint para reenviar o email de verificação
    @PostMapping("/resend-verification-email")
    public ResponseEntity<Void> resendVerificationEmail(@Valid @RequestBody ResendVerificationEmailRequest resendVerificationEmailRequest) {
        authService.resendVerificationEmail(resendVerificationEmailRequest.getEmail());
        return ResponseEntity.ok().build();
    }

    // Endpoint para login (devolve o token de acesso)
    @PostMapping("/login")
    public ResponseEntity<AuthTokensResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        AuthTokensResponse authTokensResponse = authService.login(loginRequest);
        return ResponseEntity.ok(authTokensResponse);
    }

    // Endpoint para logout (revogação do token)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorizationHeader) {
        authService.logout(authorizationHeader);
        return ResponseEntity.noContent().build();
    }

    // Endpoint para recuperação de senha (envia link de recuperação)
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest) {
        authService.forgotPassword(forgotPasswordRequest.getEmail());
        return ResponseEntity.ok().build();
    }

    // Endpoint para repor a senha com o token enviado por email
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        authService.resetPassword(resetPasswordRequest.getToken(), resetPasswordRequest.getNewPassword());
        return ResponseEntity.ok().build();
    }

    // Endpoint para obter informações do utilizador logado
    @GetMapping("/me")
    public ResponseEntity<User> getProfile(@RequestHeader("Authorization") String authorizationHeader) {
        User user = authService.getUserProfile(authorizationHeader);
        return ResponseEntity.ok(user);
    }

    // Endpoint para realizar refresh do token de acesso
    @PostMapping("/refresh-token")
    public ResponseEntity<AuthTokensResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
        AuthTokensResponse refreshedAuthTokensResponse = authService.refreshToken(refreshTokenRequest.getAccessToken(), refreshTokenRequest.getRefreshToken());
        return ResponseEntity.ok(refreshedAuthTokensResponse);
    }
}