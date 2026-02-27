package com.example.hearu.user.service;

import com.example.hearu.common.util.exception.BusinessException;
import com.example.hearu.user.domain.User;

import com.example.hearu.user.domain.error.UserSecurityErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserSecurityService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void enableAppLock(Long userId, String password) {
        // 1. User 조회 및 Throw
        User user = userService.getUserOrThrow(userId);

        // 2. password 인코딩 후, 저장
        user.updatePassword(passwordEncoder.encode(password));
    }

    @Transactional
    public void disableAppLock(Long userId) {
        // 1. User 조회 및 Throw
        User user = userService.getUserOrThrow(userId);

        // 2. User의 AppLock password null 체크
        if (!user.hasPassword()) {
            throw new BusinessException(UserSecurityErrorCode.APP_LOCK_NOT_SET);
        }

        // 3. password 초기화
        user.updatePassword(null);
    }

    @Transactional
    public void changeAppLockPassword(Long userId, String currentPassword, String newPassword) {
        // 1. User 엔티티 조회
        User user = userService.getUserOrThrow(userId);

        // 2. 앱 잠금이 설정되어 있지 않으면 예외
        if (!user.hasPassword()) {
            throw new BusinessException(UserSecurityErrorCode.APP_LOCK_NOT_SET);
        }

        // 3. 현재 비밀번호 일치 검증
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException(UserSecurityErrorCode.INVALID_APP_PASSWORD);
        }

        // 4. 새 비밀번호가 현재 비밀번호와 동일하면 예외
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException(UserSecurityErrorCode.SAME_AS_CURRENT_PASSWORD);
        }

        // 5. 새 비밀번호 인코딩 후 저장
        user.updatePassword(passwordEncoder.encode(newPassword));
    }

    @Transactional(readOnly = true)
    public void verifyAppLock(Long userId, String inputPassword) {
        // 1. User 조회 및 Throw
        User user = userService.getUserOrThrow(userId);

        // 2. User의 AppLock password null 체크
        if (!user.hasPassword()) {
            throw new BusinessException(UserSecurityErrorCode.APP_LOCK_NOT_SET);
        }

        // 3. User의 password와 입력 password가 동일한지 체크
        if (!passwordEncoder.matches(inputPassword, user.getPassword())) {
            throw new BusinessException(UserSecurityErrorCode.INVALID_APP_PASSWORD);
        }
    }
}
