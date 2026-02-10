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
        if (user.getPassword() == null) {
            throw new BusinessException(UserSecurityErrorCode.APP_LOCK_NOT_SET);
        }

        // 3. password 초기화
        user.updatePassword(null);
    }
}
