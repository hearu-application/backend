package com.example.hearu.user.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NicknameUpdateResponse {
    private final String nickname;
}