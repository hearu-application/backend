package com.example.hearu.diary.dto.response;

import com.example.hearu.diary.domain.Diary;
import com.example.hearu.diary.domain.EmotionType;

import java.time.LocalDateTime;

public record DiaryDetailResponse(
    Long diaryId,
    String content,
    EmotionType emotionType,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static DiaryDetailResponse fromEntity(Diary diary) {
        return new DiaryDetailResponse(
            diary.getDiaryId(),
            diary.getContent(),
            diary.getEmotionType(),
            diary.getCreatedAt(),
            diary.getUpdatedAt()
        );
    }
}