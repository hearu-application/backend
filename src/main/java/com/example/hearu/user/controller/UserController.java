package com.example.hearu.user.controller;

import com.example.hearu.common.response.ApiResponse;
import com.example.hearu.user.dto.request.NicknameUpdateRequest;
import com.example.hearu.user.dto.response.NicknameUpdateResponse;
import com.example.hearu.user.service.UserSecurityService;
import com.example.hearu.user.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/nickname")
    public ResponseEntity<ApiResponse<NicknameUpdateResponse>> updateNickname(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid NicknameUpdateRequest request
    ) {

        NicknameUpdateResponse updatedNickname = userService.updateNickname(userId, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "닉네임 설정 완료",
                        updatedNickname
                )
        );
    }
}
