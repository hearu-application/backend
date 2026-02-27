package com.example.hearu.user.domain;

import java.util.UUID;

import com.example.hearu.ai.character.domain.Companion;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "companion_id")
    private Companion companion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tone_type", nullable = false, length = 50)
    private ToneType toneType;

    @Column(name = "password")
    private String password;

    private User(String email, ProviderType provider, String providerUserId, Companion companion) {
        this.email = email;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.personalityType = PersonalityType.EMPATHETIC;
        this.companion = companion;
        this.toneType = ToneType.HONORIFIC;
    }

    public static User create(String email, ProviderType provider, String providerUserId, Companion companion) {
        return new User(email, provider, providerUserId, companion);
    }

    @Override
    public void softDelete() {
        super.softDelete();
        this.providerUserId = this.providerUserId + ":deleted:" + UUID.randomUUID();
    }

    public void updateNickname(String nickname) {
        this.nickName = nickname;
    }

    public void updatePassword(String password) {
        this.password = password;
    }

    public void updateCompanion(Companion companion) {
        this.companion = companion;
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

    public boolean hasPassword() {
        return this.password != null;
    }
}