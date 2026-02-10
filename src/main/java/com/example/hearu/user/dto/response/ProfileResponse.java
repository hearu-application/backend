package com.example.hearu.user.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProfileResponse {
    private final String nickname;
    private final String email;
    private boolean isAppLockEnabled;
}