package com.example.hearu.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.hearu.auth.domain.ProviderType;
import com.example.hearu.auth.service.RefreshTokenService;
import com.example.hearu.diary.service.DiaryService;
import com.example.hearu.user.domain.User;
import com.example.hearu.user.infrastructure.UserRepository;

@ExtendWith(MockitoExtension.class)
class WithdrawalPurgeServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    DiaryService diaryService;

    @Mock
    RefreshTokenService refreshTokenService;

    @InjectMocks
    WithdrawalPurgeService withdrawalPurgeService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.create("example@naver.com", ProviderType.KAKAO, "1234567890");
        ReflectionTestUtils.setField(user, "userId", 1L);
    }

    @Nested
    @DisplayName("탈퇴 유저 하드 삭제")
    class Purge {

        @Test
        @DisplayName("성공 - 일기 데이터 → refresh token → 유저 순으로 삭제한다")
        void purge_success() {
            // given
            user.softDelete();
            LocalDateTime cutoff = user.getDeletedAt().plusSeconds(1);
            given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));

            // when
            boolean purged = withdrawalPurgeService.purge(1L, cutoff);

            // then
            assertThat(purged).isTrue();
            InOrder inOrder = Mockito.inOrder(diaryService, refreshTokenService, userRepository);
            inOrder.verify(diaryService).hardDeleteAllByUserId(1L);
            inOrder.verify(refreshTokenService).deleteRefreshToken(1L);
            inOrder.verify(userRepository).delete(user);
        }

        @Test
        @DisplayName("조회 후 복구된 유저는 삭제하지 않고 건너뛴다")
        void skip_restored_user() {
            // given - 탈퇴하지 않은(복구된) 상태
            given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));

            // when
            boolean purged = withdrawalPurgeService.purge(1L, LocalDateTime.now());

            // then
            assertThat(purged).isFalse();
            verifyNoInteractions(diaryService, refreshTokenService);
            verify(userRepository, never()).delete(any(User.class));
        }

        @Test
        @DisplayName("다시 탈퇴해 유예가 새로 시작된 유저는 건너뛴다")
        void skip_user_still_in_grace() {
            // given - cutoff보다 나중에 탈퇴
            user.softDelete();
            LocalDateTime cutoff = user.getDeletedAt().minusSeconds(1);
            given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.of(user));

            // when
            boolean purged = withdrawalPurgeService.purge(1L, cutoff);

            // then
            assertThat(purged).isFalse();
            verifyNoInteractions(diaryService, refreshTokenService);
        }

        @Test
        @DisplayName("이미 삭제된 유저는 건너뛴다")
        void skip_missing_user() {
            given(userRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

            boolean purged = withdrawalPurgeService.purge(1L, LocalDateTime.now());

            assertThat(purged).isFalse();
            verifyNoInteractions(diaryService, refreshTokenService);
        }
    }
}
