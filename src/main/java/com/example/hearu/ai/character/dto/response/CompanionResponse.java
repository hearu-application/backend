package com.example.hearu.ai.character.dto.response;

import com.example.hearu.ai.character.domain.Companion;

public record CompanionResponse(

    Long companionId,
    String name,
    String description
) {

    public static CompanionResponse fromEntity(Companion companion) {
        return new CompanionResponse(
            companion.getId(),
            companion.getName(),
            companion.getDescription()
        );
    }
}
