package com.example.hearu.auth.dto.response;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String nickname
) {}
