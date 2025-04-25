package com.cardealer.models.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {

    @NotBlank(message = "AccessToken is required")
    private String accessToken;

    @NotBlank(message = "RefreshToken is required")
    private String refreshToken;
}