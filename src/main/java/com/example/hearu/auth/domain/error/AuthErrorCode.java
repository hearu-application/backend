package com.example.hearu.auth.domain.error;

import org.springframework.http.HttpStatus;

import com.example.hearu.common.util.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    MISSING_REQUIRED_CLAIMS("필수 정보가 누락되었습니다.", HttpStatus.UNAUTHORIZED),
    INVALID_ID_TOKEN("유효하지 않은 ID Token입니다.", HttpStatus.UNAUTHORIZED),
    UNSUPPORTED_OAUTH_PROVIDER("지원하지 않는 OAuth Provider입니다.", HttpStatus.BAD_REQUEST),

    INVALID_ACCESS_TOKEN("인증에 실패했습니다.", HttpStatus.UNAUTHORIZED),
    EXPIRED_ACCESS_TOKEN("Access token이 만료되었습니다.", HttpStatus.UNAUTHORIZED),

    INVALID_REFRESH_TOKEN("인증에 실패했습니다.", HttpStatus.UNAUTHORIZED),
    EXPIRED_REFRESH_TOKEN("Refresh token이 만료되었습니다.", HttpStatus.UNAUTHORIZED);

    private final String message;
    private final HttpStatus httpStatus;

}
