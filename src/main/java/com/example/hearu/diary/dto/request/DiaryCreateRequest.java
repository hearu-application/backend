package com.example.hearu.diary.dto.request;

import com.example.hearu.diary.domain.EmotionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DiaryCreateRequest(

    @NotBlank(message = "일기 내용은 필수 항목입니다.")
    @Size(max = 1_000, message = "일기 내용은 1,000자 이하이어야 합니다.")
    String content,

    @NotNull(message = "감정은 필수 항목입니다.")
    EmotionType emotionType,

    // 대상 날짜(선택, 생략 시 오늘). 범위 검증은 LocalDate.now()가 필요해 서비스에서 한다.
    LocalDate diaryDate
){}