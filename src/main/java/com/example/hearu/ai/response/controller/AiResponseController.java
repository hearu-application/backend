package com.example.hearu.ai.response.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.hearu.ai.response.dto.response.AiResponseResponse;
import com.example.hearu.ai.response.service.AiResponseService;
import com.example.hearu.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "AiResponse", description = "AI 응답 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai-responses")
public class AiResponseController {

    private final AiResponseService aiResponseService;

    @SecurityRequirement(name = "Authorization")
    @GetMapping("/{aiResponseId}")
    public ResponseEntity<ApiResponse<AiResponseResponse>> getAiResponse(
        @AuthenticationPrincipal Long userId,
        @PathVariable Long aiResponseId
    ) {
        AiResponseResponse aiResponse = aiResponseService.getAiResponse(userId, aiResponseId);
        return ResponseEntity.ok(
            ApiResponse.success(
                "AI 응답 조회가 완료되었습니다.",
                aiResponse
            )
        );
    }
}
