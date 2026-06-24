package com.example.hearu.ai.feedback.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DislikeReasonType {
    TOO_FORMAL("답장이 너무 형식적이에요"),
    NOT_UNDERSTOOD("제 일기 내용을 제대로 이해하지 못한 답장이에요"),
    TOO_AI_LIKE("말투가 너무 AI 같아요"),
    ETC("기타");

    private final String displayName;
}
