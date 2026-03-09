package com.example.hearu.diary.dto.response;

import com.example.hearu.diary.domain.EmotionType;

public record DiaryCreateResponse(

        Long diaryId,
        String content,
        EmotionType emotionType
) {
}
