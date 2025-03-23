package com.cardealer.services;

import com.cardealer.models.Company;
import com.cardealer.models.User;
import com.cardealer.models.dto.AuthDTO;
import com.cardealer.models.dto.TokenDTO;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    // Mock para representar o TokenDTO gerado após login/registro
    private static final String MOCK_TOKEN = "mock-token-123456";

    // Mock para representar o User logado
    private static final Company MOCK_COMPANY = new Company(1L, "Car Dealer Ltd.", "1234 Auto Street", null, null);
    // Mock para representar o User logado
    private static final User MOCK_USER = new User(1L, "John Doe", "john@example.com", "hashed-password", "ROLE_USER", "", MOCK_COMPANY);

    // Método de registro (simula o registro de um novo utilizador e criação de empresa)
    public TokenDTO register(AuthDTO authDTO) {
        // Aqui você faria a lógica de registro do utilizador e criação da empresa
        // Para este mock, retornamos um token simulado
        return new TokenDTO(MOCK_TOKEN, MOCK_TOKEN);
    }

    // Método para verificação de email (simula a verificação do token de email)
    public void verifyEmail(String token) {
        // Simula a verificação de token. Em um cenário real, você validaria esse token.
        // Mock: Nada a fazer.
    }

    // Método de login (simula o login e geração de um token)
    public TokenDTO login(AuthDTO authDTO) {
        // Mock: Retorna um token simulado após "validar" as credenciais do utilizador
        return new TokenDTO(MOCK_TOKEN, MOCK_TOKEN);
    }

    // Método de logout (revoga o token)
    public void logout(String authorizationHeader) {
        // Simula a revogação do token, mas no mock não faz nada
    }

    // Método para solicitação de recuperação de senha (simula o envio de um link)
    public void forgotPassword(String email) {
        // Simula o envio do link de recuperação
        // No mock, nada acontece aqui.
    }

    // Método para resetar a senha com o token (simula o reset de senha)
    public void resetPassword(String token, String password) {
        // Simula a redefinição de senha, sem efetuar nenhuma operação real
    }

    // Método para obter o perfil do utilizador logado (retorna um utilizador mockado)
    public User getUserProfile(String authorizationHeader) {
        // Retorna o utilizador mockado
        return MOCK_USER;
    }

    // Método para realizar o refresh do token (simula a geração de novos tokens)
    public TokenDTO refreshToken(String accessToken, String refreshToken) {
        // Retorna um novo token simulado
        return new TokenDTO(MOCK_TOKEN, MOCK_TOKEN);
    }
}