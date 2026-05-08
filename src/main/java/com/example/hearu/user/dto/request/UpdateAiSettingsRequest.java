package com.example.hearu.user.dto.request;

import com.example.hearu.user.domain.ToneType;

public record UpdateAiSettingsRequest(
        ToneType toneType
) {}
