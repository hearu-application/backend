package com.example.hearu.user.service;

import com.example.hearu.common.util.exception.BusinessException;
import com.example.hearu.user.domain.User;

import com.example.hearu.user.domain.error.UserSecurityErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSecurityService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void enableAppLock(Long userId, String password) {
        // 1. User 조회 및 Throw
        User user = userService.getUserOrThrow(userId);

        // 2. 앱 잠금이 이미 설정되어 있으면 예외
        if (user.hasPassword()) {
            log.warn("앱 잠금이 이미 설정되어 있습니다. userId={}", userId);
            throw new BusinessException(UserSecurityErrorCode.APP_LOCK_ALREADY_SET);
        }

        // 3. password 인코딩 후 저장
        user.updatePassword(passwordEncoder.encode(password));
        log.info("앱 잠금 설정 완료. userId={}", userId);
    }

    @Transactional
    public void disableAppLock(Long userId) {
        // 1. User 조회 및 Throw
        User user = userService.getUserOrThrow(userId);

        // 2. User의 AppLock password null 체크
        if (!user.hasPassword()) {
            log.warn("앱 잠금이 설정되어 있지 않습니다. userId={}", userId);
            throw new BusinessException(UserSecurityErrorCode.APP_LOCK_NOT_SET);
        }

        // 3. password 초기화
        user.updatePassword(null);
        log.info("앱 잠금 해제 완료. userId={}", userId);
    }

    @Transactional
    public void changeAppLockPassword(Long userId, String currentPassword, String newPassword) {
        // 1. User 엔티티 조회
        User user = userService.getUserOrThrow(userId);

        // 2. 앱 잠금이 설정되어 있지 않으면 예외
        if (!user.hasPassword()) {
            log.warn("앱 잠금이 설정되어 있지 않습니다. userId={}", userId);
            throw new BusinessException(UserSecurityErrorCode.APP_LOCK_NOT_SET);
        }

        // 3. 현재 비밀번호 일치 검증
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            log.warn("앱 잠금 비밀번호 불일치. userId={}", userId);
            throw new BusinessException(UserSecurityErrorCode.INVALID_APP_PASSWORD);
        }

        // 4. 새 비밀번호가 현재 비밀번호와 동일하면 예외
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            log.warn("새 비밀번호가 현재 비밀번호와 동일합니다. userId={}", userId);
            throw new BusinessException(UserSecurityErrorCode.SAME_AS_CURRENT_PASSWORD);
        }

        // 5. 새 비밀번호 인코딩 후 저장
        user.updatePassword(passwordEncoder.encode(newPassword));
        log.info("앱 잠금 비밀번호 변경 완료. userId={}", userId);
    }

    @Transactional(readOnly = true)
    public void verifyAppLock(Long userId, String inputPassword) {
        // 1. User 조회 및 Throw
        User user = userService.getUserOrThrow(userId);

        // 2. User의 AppLock password null 체크
        if (!user.hasPassword()) {
            log.warn("앱 잠금이 설정되어 있지 않습니다. userId={}", userId);
            throw new BusinessException(UserSecurityErrorCode.APP_LOCK_NOT_SET);
        }

        // 3. User의 password와 입력 password가 동일한지 체크
        if (!passwordEncoder.matches(inputPassword, user.getPassword())) {
            log.warn("앱 잠금 비밀번호 인증 실패. userId={}", userId);
            throw new BusinessException(UserSecurityErrorCode.INVALID_APP_PASSWORD);
        }
    }
}
