package com.cardealer.web;

import com.cardealer.models.dto.CompanyDTO;
import com.cardealer.services.CompanyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/companies")
public class CompaniesController {

    @Autowired
    private CompanyService companyService;

    @GetMapping
    public ResponseEntity<CompanyDTO> getCompany() {
        CompanyDTO companyDTO = companyService.getCompany();
        return ResponseEntity.ok(companyDTO);
    }

    @PatchMapping
    public ResponseEntity<CompanyDTO> updateCompany(@RequestBody CompanyDTO companyDTO) {
        CompanyDTO responseDTO = companyService.updateCompany(companyDTO);
        return ResponseEntity.ok(responseDTO);
    }
}