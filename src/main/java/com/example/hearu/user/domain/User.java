package com.example.hearu.user.domain;

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

    private String nickName;

    @Column(name = "email", nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private ProviderType provider;

    @Column(name = "provider_user_id", nullable = false, unique = true)
    private String providerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "personality_type", nullable = false, length = 50)
    private PersonalityType personalityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "tone_type", nullable = false, length = 50)
    private ToneType toneType;

    @Column(name = "password")
    private String password;

    private User(String email, ProviderType provider, String providerUserId) {
        this.email = email;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.personalityType = PersonalityType.EMPATHETIC;
        this.toneType = ToneType.HONORIFIC;
    }

    public static User create(String email, ProviderType provider, String providerUserId) {
        return new User(email, provider, providerUserId);
    }

    public void updateNickname(String nickname) {
        this.nickName = nickname;
    }

    public void updatePassword(String password) {
        this.password = password;
    }

    public void updatePersonalityType(PersonalityType personalityType) {
        this.personalityType = personalityType;
    }

    public void updateToneType(ToneType toneType) {
        this.toneType = toneType;
    }

    public void validateUserNickNameExists() {
        if (getNickName() == null) {
            log.error("사용자의 닉네임이 존재하지 않습니다. userId={}", getUserId());
            throw new BusinessException(UserErrorCode.NICKNAME_REQUIRED);
        }
    }
}