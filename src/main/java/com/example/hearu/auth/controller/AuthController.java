package com.example.hearu.auth.controller;

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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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
}
