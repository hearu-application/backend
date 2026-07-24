package com.example.hearu.user.service;

import com.example.hearu.auth.service.RefreshTokenService;
import com.example.hearu.common.logging.LogMasker;
import com.example.hearu.user.dto.request.NicknameUpdateRequest;
import com.example.hearu.user.dto.request.UpdateAiSettingsRequest;
import com.example.hearu.user.dto.response.NicknameUpdateResponse;
import com.example.hearu.common.util.exception.BusinessException;
import com.example.hearu.user.domain.User;
import com.example.hearu.user.domain.error.UserErrorCode;
import com.example.hearu.user.dto.response.ProfileResponse;
import com.example.hearu.user.infrastructure.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    @Transactional
    public NicknameUpdateResponse updateNickname(Long userId, NicknameUpdateRequest request) {
        User user = getUserOrThrow(userId);

        // 닉네임 업데이트
        String previousNickname = user.getNickname();
        user.updateNickname(request.nickname());
        log.debug("닉네임 변경 완료. userId={}, 기존 닉네임 존재 여부={}", userId, previousNickname != null);

        // 엔티티 대신 DTO 반환
        return new NicknameUpdateResponse(user.getNickname());
    }

    public User getUserOrThrow(Long userId) {
        return userRepository.findByUserIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> {
                log.warn("사용자가 존재하지 않습니다. userId={}", userId);
                return new BusinessException(UserErrorCode.USER_NOT_FOUND);
            });
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        // 1. User 엔티티 조회
        User user = getUserOrThrow(userId);

        log.debug("프로필 조회 완료. userId={}, email={}, toneType={}, 앱잠금 설정={}",
                userId, LogMasker.email(user.getEmail()), user.getToneType(), user.hasPassword());

        // 2. 엔티티(User)를 응답 DTO로 변환하여 반환
        return new ProfileResponse(
                user.getNickname(),
                user.getEmail(),
                user.getToneType(),
                user.hasPassword()
        );
    }

    @Transactional
    public void logout(Long userId) {
        // 1. User 조회 및 Throw
        getUserOrThrow(userId);

        // 2. Refresh Token 삭제
        refreshTokenService.deleteRefreshToken(userId);
        log.info("사용자 로그아웃 완료. userId={}", userId);
    }

    @Transactional
    public void delete(Long userId) {
        // 1. User 조회 및 Throw
        User user = getUserOrThrow(userId);

        // 2. Refresh Token hard-delete & User soft-delete
        refreshTokenService.deleteRefreshToken(userId);
        user.softDelete();
        log.info("사용자 탈퇴(soft delete) 완료. userId={}", userId);
    }

    @Transactional
    public void updateAiSettings(Long userId, UpdateAiSettingsRequest request) {
        // 1. User 엔티티 조회
        User user = getUserOrThrow(userId);

        // 2. ToneType 값이 존재하면, 업데이트
        if (request.toneType() != null) {
            log.debug("AI 응답 톤 변경. userId={}, {} -> {}",
                    userId, user.getToneType(), request.toneType());
            user.updateToneType(request.toneType());
        } else {
            log.debug("AI 설정 요청에 toneType이 없어 변경 없음. userId={}", userId);
        }
    }
}
