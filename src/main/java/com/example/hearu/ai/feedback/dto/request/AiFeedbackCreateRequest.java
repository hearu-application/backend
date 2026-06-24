package com.example.hearu.ai.feedback.dto.request;

import com.example.hearu.ai.feedback.domain.DislikeReasonType;
import com.example.hearu.ai.feedback.domain.FeedbackType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiFeedbackCreateRequest(

    @NotNull(message = "피드백 타입은 필수 항목입니다.")
    FeedbackType feedbackType,

    DislikeReasonType dislikeReasonType,

    @Size(max = 50, message = "기타 사유는 50자 이하여야 합니다.")
    String customText
){}
