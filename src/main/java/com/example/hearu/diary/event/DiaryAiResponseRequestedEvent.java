package com.example.hearu.diary.event;

import com.example.hearu.diary.domain.EmotionType;
import com.example.hearu.user.domain.ToneType;

public record DiaryAiResponseRequestedEvent(
    Long diaryId,
    String content,
    EmotionType emotionType,
    Long userId,
    String nickname,
    ToneType toneType,
    // 일기 날짜가 응답 시점(오늘)보다 며칠 전인지. 과거 날짜 일기에서 '오늘'을 그 시점 표현으로 바꿔 말하게 한다.
    long daysAgo
) {
}
