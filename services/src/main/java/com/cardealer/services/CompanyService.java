package com.cardealer.services;

import com.cardealer.models.dto.CompanyDTO;
import org.springframework.stereotype.Service;

@Service
public class CompanyService {

    // Mock da empresa
    private CompanyDTO mockCompany = new CompanyDTO(1L, "Car Dealer Ltd.", "1234 Auto Street");

    // Método para obter os dados da empresa
    public CompanyDTO getCompany() {
        return mockCompany;  // Retorna a empresa mockada
    }

    // Método para atualizar os dados da empresa
    public CompanyDTO updateCompany(CompanyDTO companyDTO) {
        // Simula a atualização da empresa, retornando o mesmo objeto atualizado
        mockCompany.setName(companyDTO.getName());
        mockCompany.setAddress(companyDTO.getAddress());
        return mockCompany;
    }
}