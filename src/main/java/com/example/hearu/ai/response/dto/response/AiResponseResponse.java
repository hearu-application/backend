package com.example.hearu.ai.response.dto.response;

import com.example.hearu.ai.response.domain.AiResponseStatusType;

public record AiResponseResponse(
    String response,
    AiResponseStatusType aiResponseStatusType
) {}
