package com.example.hearu.common.logging;

/**
 * 로그에 개인정보/자격증명이 원문으로 남지 않도록 마스킹하는 유틸리티.
 *
 * <p>환경(프로필)에 무관하게 항상 마스킹한다. dev/local에서 DEBUG 로그를 전부 켜더라도
 * 일기 본문·이메일·토큰은 원문 대신 길이나 마스킹된 형태만 기록된다.
 */
public final class LogMasker {

    private LogMasker() {
    }

    /**
     * 일기 본문 등 자유 서술 텍스트. 원문 대신 길이만 남긴다.
     */
    public static String textLength(String text) {
        if (text == null) {
            return "null";
        }
        return "len:" + text.length();
    }

    /**
     * 이메일. 앞 2글자 + 도메인만 남긴다. (예: ki****@gmail.com)
     */
    public static String email(String email) {
        if (email == null) {
            return "null";
        }
        int at = email.indexOf('@');
        if (at < 0) {
            return "invalid-email";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return "*".repeat(local.length()) + domain;
        }
        return local.substring(0, 2) + "*".repeat(local.length() - 2) + domain;
    }

    /**
     * JWT/OAuth 토큰. 원문은 절대 남기지 않고 앞 6자리 지문과 길이만 남긴다.
     */
    public static String token(String token) {
        if (token == null) {
            return "null";
        }
        if (token.length() <= 6) {
            return "***(len:" + token.length() + ")";
        }
        return token.substring(0, 6) + "***(len:" + token.length() + ")";
    }

    /**
     * OAuth provider의 sub(고유 식별자). 앞 4자리만 남긴다.
     */
    public static String sub(String sub) {
        if (sub == null) {
            return "null";
        }
        if (sub.length() <= 4) {
            return "***";
        }
        return sub.substring(0, 4) + "***";
    }
}
