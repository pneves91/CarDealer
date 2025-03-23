package com.cardealer.models.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompanyDTO {
    private Long id;          // ID da empresa (stand)
    private String name;      // Nome da empresa (stand)
    private String address;   // Endereço da empresa (stand)
}
