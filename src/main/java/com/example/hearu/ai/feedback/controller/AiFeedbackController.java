package com.example.hearu.ai.feedback.controller;

import com.example.hearu.ai.feedback.dto.request.AiFeedbackCreateRequest;
import com.example.hearu.ai.feedback.service.AiFeedbackService;
import com.example.hearu.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AiFeedback", description = "AI 응답 피드백 API")
@RestController
@RequestMapping("/api/v1/diaries")
@RequiredArgsConstructor
public class AiFeedbackController {

    private final AiFeedbackService aiFeedbackService;

    @SecurityRequirement(name = "Authorization")
    @PostMapping("/{diaryId}/ai-response/feedbacks")
    public ResponseEntity<ApiResponse<Void>> createFeedback(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long diaryId,
            @RequestBody @Valid AiFeedbackCreateRequest request
    ) {
        aiFeedbackService.createFeedback(userId, diaryId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("피드백 등록이 완료되었습니다.", null, HttpStatus.CREATED));
    }
}
