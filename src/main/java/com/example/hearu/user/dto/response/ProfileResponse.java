package com.example.hearu.user.dto.response;

import com.example.hearu.user.domain.PersonalityType;
import com.example.hearu.user.domain.ToneType;

public record ProfileResponse(
    String nickname,
    String email,
    PersonalityType personalityType,
    ToneType toneType,
    boolean hasPassword
){}