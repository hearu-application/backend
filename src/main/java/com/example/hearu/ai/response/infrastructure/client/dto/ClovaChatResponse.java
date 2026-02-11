package com.example.hearu.ai.response.infrastructure.client.dto;

public record ClovaChatResponse(
    Status status,
    Result result
)
{
    public record Status(
       String code,
       String message
    ){}

    public record Result(
       ClovaMessage clovaMessage,
       Integer inputLength,
       Integer outputLength,
       String stopReason,
       long seed
    ){}
}
