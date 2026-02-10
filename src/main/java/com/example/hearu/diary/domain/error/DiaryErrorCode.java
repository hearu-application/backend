package com.example.hearu.diary.domain.error;

import org.springframework.http.HttpStatus;

import com.example.hearu.common.util.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DiaryErrorCode implements ErrorCode {

    DIARY_NOT_FOUND("일기를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    DIARY_ACCESS_DENIED("일기 접근 권한이 없습니다.", HttpStatus.FORBIDDEN);

    private final String message;
    private final HttpStatus httpStatus;

}
