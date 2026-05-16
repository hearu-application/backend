package com.example.hearu.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.hearu.auth.domain.ProviderType;
import com.example.hearu.common.util.exception.BusinessException;
import com.example.hearu.user.domain.User;

@ExtendWith(MockitoExtension.class)
public class UserSecurityServiceTest {

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    UserService userService;

    @InjectMocks
    private UserSecurityService userSecurityService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.create(
            "example@naver.com",
            ProviderType.KAKAO,
            "1234567890"
        );
    }

    @Nested
    @DisplayName("앱 잠금 설정 (enableAppLock)")
    class EnableAppLock {

        @Test
        @DisplayName("성공 - 비밀번호 인코딩 후 저장")
        void success() {
            // given
            given(userService.getUserOrThrow(1L)).willReturn(user);
            given(passwordEncoder.encode("1234")).willReturn("encoded1234");

            // when
            userSecurityService.enableAppLock(1L, "1234");

            // then
            assertThat(user.hasPassword()).isTrue();
            assertThat(user.getPassword()).isEqualTo("encoded1234");
        }

        @Test
        @DisplayName("앱 잠금이 이미 설정된 경우 예외 발생")
        void already_set() {
            // given
            given(userService.getUserOrThrow(1L)).willReturn(user);
            user.updatePassword("encoded1234");

            // when & then
            assertThatThrownBy(() -> userSecurityService.enableAppLock(1L, "1234"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("앱 잠금 비밀번호가 이미 설정되어 있습니다.");
        }
    }

    @Nested
    @DisplayName("앱 잠금 해제 (disableAppLock)")
    class DisableAppLock {

        @Test
        @DisplayName("성공 - 비밀번호 null로 초기화")
        void success() {
            // given
            given(userService.getUserOrThrow(1L)).willReturn(user);
            user.updatePassword("encoded1234");

            // when
            userSecurityService.disableAppLock(1L);

            // then
            assertThat(user.hasPassword()).isFalse();
        }

        @Test
        @DisplayName("앱 잠금이 설정되어 있지 않은 경우 예외 발생")
        void not_set() {
            // given
            given(userService.getUserOrThrow(1L)).willReturn(user);

            // when & then
            assertThatThrownBy(() -> userSecurityService.disableAppLock(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("앱 잠금 비밀번호가 설정되지 않았습니다.");
        }
    }

    @Nested
    @DisplayName("앱 잠금 비밀번호 변경 (changeAppLockPassword)")
    class ChangeAppLockPassword {

        @Test
        @DisplayName("성공 - 새 비밀번호로 변경")
        void success() {
            // given
            given(userService.getUserOrThrow(1L)).willReturn(user);
            user.updatePassword("encodedOld");
            given(passwordEncoder.matches("old", "encodedOld")).willReturn(true);
            given(passwordEncoder.matches("new", "encodedOld")).willReturn(false);
            given(passwordEncoder.encode("new")).willReturn("encodedNew");

            // when
            userSecurityService.changeAppLockPassword(1L, "old", "new");

            // then
            assertThat(user.getPassword()).isEqualTo("encodedNew");
        }

        @Test
        @DisplayName("앱 잠금이 설정되어 있지 않은 경우 예외 발생")
        void not_set() {
            // given
            given(userService.getUserOrThrow(1L)).willReturn(user);

            // when & then
            assertThatThrownBy(() -> userSecurityService.changeAppLockPassword(1L, "old", "new"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("앱 잠금 비밀번호가 설정되지 않았습니다.");
        }

        @Test
        @DisplayName("현재 비밀번호가 일치하지 않는 경우 예외 발생")
        void invalid_current_password() {
            // given
            given(userService.getUserOrThrow(1L)).willReturn(user);
            user.updatePassword("encodedOld");
            given(passwordEncoder.matches("wrong", "encodedOld")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> userSecurityService.changeAppLockPassword(1L, "wrong", "new"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("앱 잠금 비밀번호가 일치하지 않습니다.");
        }

        @Test
        @DisplayName("새 비밀번호가 현재 비밀번호와 동일한 경우 예외 발생")
        void same_as_current_password() {
            // given
            given(userService.getUserOrThrow(1L)).willReturn(user);
            user.updatePassword("encodedOld");
            given(passwordEncoder.matches("old", "encodedOld")).willReturn(true);
            given(passwordEncoder.matches("old", "encodedOld")).willReturn(true);

            // when & then
            assertThatThrownBy(() -> userSecurityService.changeAppLockPassword(1L, "old", "old"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("새 비밀번호가 현재 비밀번호와 동일합니다.");
        }
    }

    @Nested
    @DisplayName("앱 잠금 인증 (verifyAppLock)")
    class VerifyAppLock {

        @Test
        @DisplayName("성공 - 비밀번호 일치")
        void success() {
            // given
            given(userService.getUserOrThrow(1L)).willReturn(user);
            user.updatePassword("encoded1234");
            given(passwordEncoder.matches("1234", "encoded1234")).willReturn(true);

            // when & then
            assertThatCode(() -> userSecurityService.verifyAppLock(1L, "1234"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("앱 잠금이 설정되어 있지 않은 경우 예외 발생")
        void not_set() {
            // given
            given(userService.getUserOrThrow(1L)).willReturn(user);

            // when & then
            assertThatThrownBy(() -> userSecurityService.verifyAppLock(1L, "1234"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("앱 잠금 비밀번호가 설정되지 않았습니다.");
        }

        @Test
        @DisplayName("비밀번호가 일치하지 않는 경우 예외 발생")
        void invalid_password() {
            // given
            given(userService.getUserOrThrow(1L)).willReturn(user);
            user.updatePassword("encoded1234");
            given(passwordEncoder.matches("wrong", "encoded1234")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> userSecurityService.verifyAppLock(1L, "wrong"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("앱 잠금 비밀번호가 일치하지 않습니다.");
        }
    }
}
