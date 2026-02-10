package com.example.hearu.user.service;

import com.example.hearu.auth.service.RefreshTokenService;
import com.example.hearu.user.dto.request.NicknameUpdateRequest;
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
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public NicknameUpdateResponse updateNickname(Long userId, NicknameUpdateRequest request) {
        User user = getUserOrThrow(userId);

        // 닉네임 업데이트
        user.updateNickname(request.getNickname());

        // 엔티티 대신 DTO 반환
        return NicknameUpdateResponse.builder()
                .nickname(user.getNickName())
                .build();
    }

    @Transactional(readOnly = true)
    public User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> {
                log.warn("사용자가 존재하지 않습니다. userId={}", userId);
                return new BusinessException(UserErrorCode.USER_NOT_FOUND);
            });
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) { // 반환 타입을 User -> ProfileResponseDto로 변경
        User user = getUserOrThrow(userId);

        // 엔티티(User)를 응답 DTO로 변환하여 반환
        return ProfileResponse.builder()
                .nickname(user.getNickName())
                .email(user.getEmail())
                .isAppLockEnabled(user.getPassword() != null)
                .build();
    }

    @Transactional
    public void logout(Long userId) {
        // 1. User 조회 및 Throw
        getUserOrThrow(userId);

        // 2. Refresh Token 삭제
        refreshTokenService.deleteRefreshToken(userId);
    }
}