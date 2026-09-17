package com.example.hearu.diary.dto.response;

import com.example.hearu.diary.domain.EmotionType;

import java.time.LocalDate;

public record DiaryCreateResponse(

        Long diaryId,
        String content,
        EmotionType emotionType,
        LocalDate diaryDate
) {
}
