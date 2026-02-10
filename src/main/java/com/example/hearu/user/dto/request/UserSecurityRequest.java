package com.example.hearu.user.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UserSecurityRequest(

    @NotNull(message = "비밀번호 필드는 필수입니다.")
    @Pattern(regexp = "^\\d{4}$", message = "비밀번호는 숫자 4자리입니다.")
    String password
){}