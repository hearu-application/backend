package com.example.hearu.user.domain;

import com.example.hearu.common.entity.BaseEntity;
import com.example.hearu.common.util.exception.BusinessException;
import com.example.hearu.diary.domain.Diary;
import com.example.hearu.auth.domain.ProviderType;

import com.example.hearu.user.domain.error.UserErrorCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.extern.slf4j.Slf4j;


import java.util.ArrayList;
import java.util.List;

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

    @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private final List<Diary> diaries = new ArrayList<>();

    @Column(name = "password")
    private String password;

    private User(String email, ProviderType provider, String providerUserId) {
        this.email = email;
        this.provider = provider;
        this.providerUserId = providerUserId;
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

    public void validateUserNickNameExists() {
        if (getNickName() == null) {
            log.error("사용자의 닉네임이 존재하지 않습니다. userId={}", getUserId());
            throw new BusinessException(UserErrorCode.NICKNAME_REQUIRED);
        }
    }
}