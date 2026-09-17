package com.example.hearu.diary.domain.error;

import org.springframework.http.HttpStatus;

import com.example.hearu.common.util.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DiaryErrorCode implements ErrorCode {

    DIARY_NOT_FOUND("일기를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    DIARY_ACCESS_DENIED("일기 접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    DIARY_DAILY_LIMIT_EXCEEDED("오늘은 더 이상 일기를 작성할 수 없습니다.", HttpStatus.TOO_MANY_REQUESTS),
    DIARY_DATE_OUT_OF_RANGE("작성 가능한 날짜 범위를 벗어났습니다.", HttpStatus.BAD_REQUEST),;

    private final String message;
    private final HttpStatus httpStatus;

}
