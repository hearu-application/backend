package com.example.hearu.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OauthRequest(@NotBlank String idToken) {
}
