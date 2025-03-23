package com.cardealer.models.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {
    private Long id;          // ID do utilizador
    private String name;      // Nome do utilizador
    private String email;     // Email do utilizador
    private String role;      // Role do utilizador (exemplo: ADMIN, MANAGER, etc.)
    private String companyName; // Nome da empresa (stand) associada ao utilizador
}