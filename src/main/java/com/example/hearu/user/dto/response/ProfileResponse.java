package com.example.hearu.user.dto.response;

import com.example.hearu.user.domain.ToneType;

public record ProfileResponse(
    String nickname,
    String email,
    ToneType toneType,
    boolean hasPassword
){}
