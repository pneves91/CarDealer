package com.cardealer.models.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResendVerificationEmailRequest {

    @NotBlank(message = "O email é obrigatório")
    @Email(message = "Email inválido")
    private String email;
}