package com.example.hearu.ai.character.domain.error;

import org.springframework.http.HttpStatus;

import com.example.hearu.common.util.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CompanionErrorCode implements ErrorCode {

    COMPANION_NOT_FOUND("캐릭터를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

    private final String message;
    private final HttpStatus httpStatus;
}
