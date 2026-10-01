package com.example.hearu.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OauthRequest(

    @NotBlank(message = "idToken 값은 필수입니다.")
    String idToken,

    // 탈퇴 유예 복구 안내를 처리할 수 있는 앱만 true로 보낸다. 구버전 앱은 이 필드를 보내지 않아
    // false로 바인딩되고, 기존처럼 신규 가입으로 처리된다.
    boolean withdrawalRestoreSupported
) {}
