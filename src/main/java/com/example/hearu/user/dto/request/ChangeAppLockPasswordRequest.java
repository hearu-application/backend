package com.example.hearu.user.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ChangeAppLockPasswordRequest(

    @NotNull(message = "현재 비밀번호 필드는 필수입니다.")
    @Pattern(regexp = "^\\d{4}$", message = "비밀번호는 숫자 4자리입니다.")
    String currentPassword,

    @NotNull(message = "새 비밀번호 필드는 필수입니다.")
    @Pattern(regexp = "^\\d{4}$", message = "비밀번호는 숫자 4자리입니다.")
    String newPassword
) {}
