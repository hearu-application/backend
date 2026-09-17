package com.example.hearu.diary.dto.response;

import com.example.hearu.diary.domain.EmotionType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DiaryDetailResponse(
    Long diaryId,
    String content,
    EmotionType emotionType,
    LocalDate diaryDate,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}