package com.example.hearu.diary.event;

import com.example.hearu.diary.domain.EmotionType;

public record DiaryAiResponseRequestedEvent(
    Long diaryId,
    String content,
    EmotionType emotionType,
    Long userId,
    String nickname
) {
}
