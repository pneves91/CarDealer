package com.cardealer.web;

import com.cardealer.models.dto.AuthDTO;
import com.cardealer.models.dto.TokenDTO;
import com.cardealer.models.User;
import com.cardealer.services.AuthService;
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
    public ResponseEntity<TokenDTO> register(@RequestBody AuthDTO authDTO) {
        TokenDTO tokenDTO = authService.register(authDTO);
        return ResponseEntity.status(201).body(tokenDTO);
    }

    // Endpoint para verificação de email (envio de código de verificação)
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestBody AuthDTO authDTO) {
        authService.verifyEmail(authDTO.getToken());
        return ResponseEntity.ok().build();
    }

    // Endpoint para login (devolve o token de acesso)
    @PostMapping("/login")
    public ResponseEntity<TokenDTO> login(@RequestBody AuthDTO authDTO) {
        TokenDTO tokenDTO = authService.login(authDTO);
        return ResponseEntity.ok(tokenDTO);
    }

    // Endpoint para logout (revogação do token)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorizationHeader) {
        authService.logout(authorizationHeader);
        return ResponseEntity.noContent().build();
    }

    // Endpoint para recuperação de senha (envia link de recuperação)
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@RequestBody AuthDTO authDTO) {
        authService.forgotPassword(authDTO.getEmail());
        return ResponseEntity.ok().build();
    }

    // Endpoint para repor a senha com o token enviado por email
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody AuthDTO authDTO) {
        authService.resetPassword(authDTO.getToken(), authDTO.getPassword());
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
    public ResponseEntity<TokenDTO> refreshToken(@RequestBody TokenDTO tokenDTO) {
        TokenDTO refreshedTokenDTO = authService.refreshToken(tokenDTO.getAccessToken(), tokenDTO.getRefreshToken());
        return ResponseEntity.ok(refreshedTokenDTO);
    }
}