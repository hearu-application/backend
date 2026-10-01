package com.example.hearu.auth.dto.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String nickname,

    // 유예 중인 탈퇴 계정이 있을 때만 채워진다. null이면 키 자체를 내보내지 않아 구버전 앱이 받는
    // 응답 JSON은 이 필드가 생기기 전과 같다.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    PendingWithdrawal pendingWithdrawal
) {

    public static AuthResponse issued(String accessToken, String refreshToken, String nickname) {
        return new AuthResponse(accessToken, refreshToken, nickname, null);
    }

    public static AuthResponse withdrawalPending(LocalDateTime purgeAt) {
        return new AuthResponse(null, null, null, new PendingWithdrawal(purgeAt));
    }

    public record PendingWithdrawal(LocalDateTime purgeAt) {}
}
