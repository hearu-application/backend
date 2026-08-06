package com.example.hearu.diary.event;

import com.example.hearu.diary.domain.EmotionType;
import com.example.hearu.user.domain.ToneType;

public record DiaryAiResponseRequestedEvent(
    Long diaryId,
    String content,
    EmotionType emotionType,
    Long userId,
    String nickname,
    ToneType toneType
) {
}
