package com.example.hearu.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hearu.ai.character.domain.Companion;
import com.example.hearu.ai.character.service.CompanionService;
import com.example.hearu.auth.domain.ProviderType;
import com.example.hearu.auth.service.RefreshTokenService;
import com.example.hearu.common.util.exception.BusinessException;
import com.example.hearu.user.domain.ToneType;
import com.example.hearu.user.domain.User;
import com.example.hearu.user.dto.request.NicknameUpdateRequest;
import com.example.hearu.user.dto.request.UpdateAiSettingsRequest;
import com.example.hearu.user.dto.response.NicknameUpdateResponse;
import com.example.hearu.user.dto.response.ProfileResponse;
import com.example.hearu.user.infrastructure.UserRepository;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    RefreshTokenService refreshTokenService;

    @Mock
    CompanionService companionService;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    public void setUp() {
        Companion companion = Companion.create(
            "봉봉이",
            "설명",
            "페르소나",
            "행동규칙",
            "예시"
        );

        user = User.create(
            "example@naver.com",
            ProviderType.KAKAO,
            "1234567890",
            companion
        );
    }

    @Test
    @DisplayName("사용자가 없는 경우, 예외 처리")
    void user_not_found() {
        // given
        given(userRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getUserOrThrow(1L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("사용자를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("사용자가 있는 경우, User 엔티티 반환")
    void user_found() {
        // given
        given(userRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));

        // when
        User userOrThrow = userService.getUserOrThrow(1L);

        // then
        assertThat(userOrThrow).isSameAs(user);
    }

    @Test
    @DisplayName("닉네임 업데이트 사용자가 없는 경우, 예외 처리")
    void update_nickname_user_not_found() {
        // given
        given(userRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());
        NicknameUpdateRequest request = new NicknameUpdateRequest("용준");

        // when & then
        assertThatThrownBy(() -> userService.updateNickname(1L, request))
            .isInstanceOf(BusinessException.class)
            .hasMessage("사용자를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("닉네임 업데이트 성공")
    void update_nickname() {
        // given
        given(userRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        NicknameUpdateRequest request = new NicknameUpdateRequest("용준");

        // when
        NicknameUpdateResponse nicknameUpdateResponse = userService.updateNickname(1L, request);

        // then
        assertThat(nicknameUpdateResponse.nickname()).isEqualTo("용준");
    }

    @Test
    @DisplayName("프로필 조회 사용자가 없는 경우, 예외 처리")
    void profile_found_user_not_found() {
        // given
        given(userRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getProfile(1L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("사용자를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("프로필 조회 성공")
    void profile_found() {
        // given
        given(userRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        user.updateNickname("용준");

        // when
        ProfileResponse profile = userService.getProfile(1L);

        // then
        assertThat(profile.nickname()).isEqualTo(user.getNickName());
        assertThat(profile.email()).isEqualTo(user.getEmail());
        assertThat(profile.toneType()).isEqualTo(user.getToneType());
    }

    @Test
    @DisplayName("로그아웃 사용자가 없는 경우, 예외 처리")
    void logout_user_not_found() {
        // given
        given(userRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.logout(1L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("사용자를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("로그아웃 성공")
    void logout() {
        // given
        given(userRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));

        // when
        userService.logout(1L);

        // then
        verify(refreshTokenService, times(1)).deleteRefreshToken(1L);
    }

    @Test
    @DisplayName("회원탈퇴 사용자가 없는 경우, 예외 처리")
    void user_delete_user_not_found() {
        // given
        given(userRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.delete(1L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("사용자를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("회원탈퇴 성공")
    void user_delete() {
        // given
        given(userRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));

        // when
        userService.delete(1L);

        // then
        verify(refreshTokenService, times(1)).deleteRefreshToken(1L);
        assertThat(user.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("AI 설정 ToneType 사용자가 없는 경우, 예외 처리")
    void update_ai_settings_user_not_found() {
        // given
        given(userRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());
        UpdateAiSettingsRequest request = new UpdateAiSettingsRequest(1L, ToneType.INFORMAL);

        // when & then
        assertThatThrownBy(() -> userService.updateAiSettings(1L, request))
            .isInstanceOf(BusinessException.class)
            .hasMessage("사용자를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("AI 설정 companionId와 ToneType 필드가 null이 아닌 경우")
    void update_ai_settings_tone_type_is_not_null() {
        // given
        given(userRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        Companion companion = Companion.create(
            "빙빙이",
            "설명",
            "페르소나",
            "행동규칙",
            "예시"
        );
        given(companionService.getOrThrow(2L)).willReturn(companion);
        UpdateAiSettingsRequest request = new UpdateAiSettingsRequest(2L, ToneType.INFORMAL);

        // when
        userService.updateAiSettings(1L, request);

        // then
        assertThat(user.getToneType()).isEqualTo(ToneType.INFORMAL);
        assertThat(user.getCompanion()).isSameAs(companion);
    }

    @Test
    @DisplayName("AI 설정 companionId와 ToneType 필드가 null인 경우")
    void update_ai_settings_tone_type_is_null() {
        // given
        given(userRepository.findByUserIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        ToneType original = user.getToneType();
        UpdateAiSettingsRequest request = new UpdateAiSettingsRequest(null,null);

        // when
        userService.updateAiSettings(1L, request);

        // then
        assertThat(user.getToneType()).isEqualTo(original);
    }
}
