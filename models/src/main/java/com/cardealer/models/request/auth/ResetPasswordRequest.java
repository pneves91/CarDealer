package com.cardealer.models.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank(message = "O token é obrigatório")
    private String token;

    @NotBlank(message = "A nova password é obrigatória")
    @Size(min = 8, message = "A nova password deve ter pelo menos 8 caracteres")
    private String newPassword;
}