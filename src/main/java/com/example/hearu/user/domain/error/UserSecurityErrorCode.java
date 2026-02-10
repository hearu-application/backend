package com.example.hearu.user.domain.error;

import org.springframework.http.HttpStatus;
import com.example.hearu.common.util.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 */
@Getter
@RequiredArgsConstructor
public enum UserSecurityErrorCode implements ErrorCode {

    APP_LOCK_NOT_SET("앱 잠금 비밀번호가 설정되지 않았습니다.", HttpStatus.BAD_REQUEST),
    INVALID_APP_PASSWORD("앱 잠금 비밀번호가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED);

    private final String message;
    private final HttpStatus httpStatus;
}