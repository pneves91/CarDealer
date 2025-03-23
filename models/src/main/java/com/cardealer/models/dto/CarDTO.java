package com.cardealer.models.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CarDTO {
    private Long id;          // ID do carro
    private String brand;     // Marca do carro
    private String model;     // Modelo do carro
    private double price;     // Preço do carro
    private String companyName; // Nome da empresa (stand) associada ao carro
}
