package com.example.hearu.user.domain.error;

import org.springframework.http.HttpStatus;

import com.example.hearu.common.util.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    NICKNAME_REQUIRED("닉네임 설정이 필요합니다.", HttpStatus.CONFLICT);

    private final String message;
    private final HttpStatus httpStatus;

}