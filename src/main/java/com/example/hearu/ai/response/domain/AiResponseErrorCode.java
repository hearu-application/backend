package com.example.hearu.ai.response.domain;

import com.example.hearu.common.util.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiResponseErrorCode implements ErrorCode {

    AI_RESPONSE_NOT_FOUND("AI 응답을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    AI_RESPONSE_ALREADY_COMPLETED("이미 AI 응답이 완료된 일기입니다.", HttpStatus.CONFLICT);

    private final String message;
    private final HttpStatus httpStatus;
}
