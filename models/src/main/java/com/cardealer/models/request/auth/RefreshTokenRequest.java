package com.cardealer.models.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {

    @NotBlank(message = "O accessToken é obrigatório")
    private String accessToken;

    @NotBlank(message = "O refreshToken é obrigatório")
    private String refreshToken;
}