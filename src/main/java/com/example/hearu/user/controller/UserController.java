package com.example.hearu.user.controller;

import com.example.hearu.common.response.ApiResponse;
import com.example.hearu.user.dto.request.NicknameUpdateRequest;
import com.example.hearu.user.dto.request.UpdateAiSettingsRequest;
import com.example.hearu.user.dto.request.UserSecurityRequest;
import com.example.hearu.user.dto.response.NicknameUpdateResponse;
import com.example.hearu.user.dto.response.ProfileResponse;
import com.example.hearu.user.service.UserSecurityService;
import com.example.hearu.user.service.UserService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "User API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserSecurityService userSecurityService;

    @SecurityRequirement(name = "Authorization")
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

    @SecurityRequirement(name = "Authorization")
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(
            @AuthenticationPrincipal Long userId
    ) {

        ProfileResponse profile = userService.getProfile(userId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "프로필 정보 조회 완료",
                        profile
                )
        );
    }

    @SecurityRequirement(name = "Authorization")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal Long userId
    ) {
        userService.logout(userId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "사용자가 로그아웃 완료",
                        null
                )
        );
    }

    @SecurityRequirement(name = "Authorization")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> delete (
            @AuthenticationPrincipal Long userId
    ) {
        userService.delete(userId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "사용자 회원탈퇴 완료",
                        null
                )
        );
    }

    @SecurityRequirement(name = "Authorization")
    @PatchMapping("/ai-settings")
    public ResponseEntity<ApiResponse<Void>> updateAiSettings(
            @AuthenticationPrincipal Long userId,
            @RequestBody UpdateAiSettingsRequest request
    ) {
        userService.updateAiSettings(userId, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "응답 설정이 완료되었습니다.",
                        null
                )
        );
    }



    // ----------------------------------------------------------------------- //


    @SecurityRequirement(name = "Authorization")
    @PatchMapping("/lock-setting/enable")
    public ResponseEntity<ApiResponse<Void>> enableAppLock(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid UserSecurityRequest request
    ) {

        userSecurityService.enableAppLock(userId, request.password());

        return ResponseEntity.ok(
                ApiResponse.success(
                        "앱 잠금 설정이 완료되었습니다.",
                        null
                )
        );
    }

    @SecurityRequirement(name = "Authorization")
    @PatchMapping("/lock-setting/disable")
    public ResponseEntity<ApiResponse<Void>> disableAppLock(
            @AuthenticationPrincipal Long userId
    ) {

        userSecurityService.disableAppLock(userId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "앱 잠금이 해제되었습니다.",
                        null
                )
        );
    }

    @SecurityRequirement(name = "Authorization")
    @PostMapping("/lock-setting/verify")
    public ResponseEntity<ApiResponse<Void>> verifyAppLock(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid UserSecurityRequest request
    ) {

        userSecurityService.verifyAppLock(userId, request.password());

        return ResponseEntity.ok(
                ApiResponse.success(
                        "비밀번호 인증에 성공했습니다.",
                        null
                )
        );
    }
}
