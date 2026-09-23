package com.example.hearu.common.cert;

/**
 * 서빙 중인 TLS 인증서 판독 실패(연결·핸드셰이크 실패 등). 갱신 실패의 직접 신호이므로
 * BusinessException이 아니라 CertExpiryMonitor의 @Retryable/@Recover가 그대로 처리한다.
 */
public class CertInspectionException extends RuntimeException {

    public CertInspectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
