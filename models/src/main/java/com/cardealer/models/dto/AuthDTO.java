package com.cardealer.models.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthDTO {
    private String email;
    private String password;
    private String name; // Usado no registro de novos utilizadores
    private String token; // Usado para verificação de email ou reset de senha
}
