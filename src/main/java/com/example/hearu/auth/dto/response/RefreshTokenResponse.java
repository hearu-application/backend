package com.example.hearu.auth.dto.response;

public record RefreshTokenResponse(
        String accessToken,
        String refreshToken
) {}
