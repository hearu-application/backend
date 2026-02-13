package com.example.hearu.user.dto.request;

import com.example.hearu.user.domain.PersonalityType;
import com.example.hearu.user.domain.ToneType;

public record UpdateAiSettingsRequest(
        PersonalityType personalityType,
        ToneType toneType
) {}