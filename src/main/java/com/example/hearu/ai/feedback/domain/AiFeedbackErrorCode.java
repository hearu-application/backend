package com.example.hearu.ai.feedback.domain;

import com.example.hearu.common.util.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiFeedbackErrorCode implements ErrorCode {

    REASON_TEXT_REQUIRED("기타 사유 선택 시 내용을 입력해야 합니다.", HttpStatus.BAD_REQUEST);

    private final String message;
    private final HttpStatus httpStatus;
}
