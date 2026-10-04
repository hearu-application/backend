package com.example.hearu.user.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.hearu.common.entity.BaseEntity;
import com.example.hearu.common.util.exception.BusinessException;
import com.example.hearu.auth.domain.ProviderType;

import com.example.hearu.user.domain.error.UserErrorCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(name = "nickname")
    private String nickname;

    @Column(name = "email", nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private ProviderType provider;

    @Column(name = "provider_user_id", nullable = false, unique = true)
    private String providerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tone_type", nullable = false, length = 50)
    private ToneType toneType;

    @Column(name = "password")
    private String password;

    private User(String email, ProviderType provider, String providerUserId) {
        this.email = email;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.toneType = ToneType.INFORMAL;
    }

    public static User create(String email, ProviderType provider, String providerUserId) {
        return new User(email, provider, providerUserId);
    }

    // 탈퇴 시 providerUserId 뒤에 붙는 표식. 유니크 키(provider_user_id)를 비워 같은 소셜 계정의
    // 재가입을 허용하고, 유예 중 복구할 때는 이 접두사(원래 sub + 표식)로 탈퇴 계정을 찾는다.
    private static final String WITHDRAWN_MARKER = ":deleted:";

    public static String withdrawnProviderUserIdPrefix(String sub) {
        return sub + WITHDRAWN_MARKER;
    }

    @Override
    public void softDelete() {
        super.softDelete();
        this.providerUserId = withdrawnProviderUserIdPrefix(this.providerUserId) + UUID.randomUUID();
    }

    public void restore(String sub) {
        undoSoftDelete();
        this.providerUserId = sub;
    }

    public boolean isWithdrawnBefore(LocalDateTime cutoff) {
        return getDeletedAt() != null && getDeletedAt().isBefore(cutoff);
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updatePassword(String password) {
        this.password = password;
    }

    public void updateToneType(ToneType toneType) {
        this.toneType = toneType;
    }

    public void validateNicknameExists() {
        if (getNickname() == null) {
            log.warn("사용자의 닉네임이 존재하지 않습니다. userId={}", getUserId());
            throw new BusinessException(UserErrorCode.NICKNAME_REQUIRED);
        }
    }

    public boolean hasPassword() {
        return this.password != null;
    }
}