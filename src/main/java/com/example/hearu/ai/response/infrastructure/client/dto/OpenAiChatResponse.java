package com.example.hearu.ai.response.infrastructure.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenAiChatResponse(
    List<Choice> choices,
    Usage usage
)
{
    public record Choice(
        ResponseMessage message,
        @JsonProperty("finish_reason") String finishReason
    ) {}

    public record ResponseMessage(
        String role,
        String content,
        String refusal
    ) {}

    public record Usage(
        @JsonProperty("prompt_tokens") Integer promptTokens,
        @JsonProperty("completion_tokens") Integer completionTokens,
        @JsonProperty("completion_tokens_details") CompletionTokensDetails completionTokensDetails
    ) {}

    public record CompletionTokensDetails(
        @JsonProperty("reasoning_tokens") Integer reasoningTokens
    ) {}
}
