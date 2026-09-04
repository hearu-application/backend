package com.example.hearu.common.encrypt;

/**
 * 본문 암복호화 실패. 키 설정 오류·저장값 무결성 위반(변조) 등 복구 불가능한 상태이므로
 * BusinessException이 아니라 그대로 던져 500으로 처리한다(GlobalExceptionHandler의
 * catch-all Exception 핸들러가 로그·응답을 남긴다).
 */
public class ContentCryptoException extends RuntimeException {

    public ContentCryptoException(String message) {
        super(message);
    }

    public ContentCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
