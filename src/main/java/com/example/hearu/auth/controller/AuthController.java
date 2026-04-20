package com.example.hearu.auth.controller;

import com.example.hearu.auth.dto.request.RefreshTokenRequest;
import com.example.hearu.auth.dto.response.RefreshTokenResponse;
import com.example.hearu.auth.service.RefreshTokenService;
import com.example.hearu.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.hearu.auth.domain.ProviderType;
import com.example.hearu.auth.dto.request.OauthRequest;
import com.example.hearu.auth.dto.response.AuthResponse;
import com.example.hearu.auth.service.AuthService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/oauth/{provider}")
    public ResponseEntity<ApiResponse<AuthResponse>> authenticate(
        @PathVariable ProviderType provider,
        @RequestBody @Valid OauthRequest request
    ) {
        AuthResponse authResponse = authService.registerOrLogin(
                provider,
                request
        );
        return ResponseEntity.ok(
                ApiResponse.success(
                        "인증에 성공하였습니다.",
                        authResponse
                )
        );
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> getAccessToken(
            @RequestBody @Valid RefreshTokenRequest request
    ) {
        RefreshTokenResponse response = refreshTokenService.getNewRefreshTokenAndAccessToken(request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Access Token 발급에 성공했습니다.",
                        response
                )
        );
    }
}
