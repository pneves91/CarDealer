package com.cardealer.models.response.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthTokensResponse {
    private String accessToken;
    private String refreshToken;
}
