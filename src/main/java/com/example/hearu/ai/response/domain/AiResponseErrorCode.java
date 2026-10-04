package com.example.hearu.ai.response.domain;

import com.example.hearu.common.util.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiResponseErrorCode implements ErrorCode {

    AI_RESPONSE_NOT_FOUND("AI 응답을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

    private final String message;
    private final HttpStatus httpStatus;
}
