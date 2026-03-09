package com.example.hearu.diary.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmotionType {
    JOY("기쁨"),
    SADNESS("슬픔"),
    ANGER("분노"),
    CALM("평온함"),
    NEUTRAL("보통");

    private final String displayName;
}