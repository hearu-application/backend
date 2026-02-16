package com.example.hearu.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OauthRequest(

    @NotBlank(message = "idToken 값은 필수입니다.")
    String idToken
) {}
