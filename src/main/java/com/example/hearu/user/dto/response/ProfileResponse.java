package com.example.hearu.user.dto.response;

import com.example.hearu.user.domain.ToneType;

public record ProfileResponse(
    String nickname,
    String email,
    Long companionId,
    ToneType toneType,
    boolean hasPassword
){}