package com.example.hearu.ai.response.service;

import static org.assertj.core.api.AssertionsForInterfaceTypes.*;
import static org.mockito.BDDMockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.hearu.ai.response.domain.AiResponse;
import com.example.hearu.ai.response.domain.AiResponseErrorCode;
import com.example.hearu.ai.response.domain.AiResponseStatusType;
import com.example.hearu.ai.response.dto.response.AiResponseResponse;
import com.example.hearu.ai.response.infrastructure.repository.AiResponseRepository;
import com.example.hearu.auth.domain.ProviderType;
import com.example.hearu.common.util.exception.BusinessException;
import com.example.hearu.diary.domain.Diary;
import com.example.hearu.diary.domain.EmotionType;
import com.example.hearu.diary.domain.error.DiaryErrorCode;
import com.example.hearu.user.domain.User;

@ExtendWith(MockitoExtension.class)
public class AiResponseServiceTest {

    @Mock
    AiResponseRepository aiResponseRepository;

    @InjectMocks
    AiResponseService aiResponseService;

    private AiResponse aiResponse;

    @BeforeEach
    void setUp() {
        User user = User.create(
                "example@naver.com",
                ProviderType.KAKAO,
                "1234567890"
        );
        ReflectionTestUtils.setField(user, "userId", 1L);

        Diary diary = Diary.create(user, "내용", EmotionType.JOY);
        ReflectionTestUtils.setField(diary, "diaryId", 1L);

        aiResponse = AiResponse.create(diary);
        ReflectionTestUtils.setField(aiResponse, "aiResponseId", 1L);
        ReflectionTestUtils.setField(aiResponse, "content", "테스트 AI 응답입니다.");
    }

    @Nested
    @DisplayName("AI 응답 조회")
    class GetAiResponse {

        @Test
        @DisplayName("AI 응답이 없는 경우, 예외 처리")
        void ai_response_not_found() {
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> aiResponseService.getAiResponse(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AiResponseErrorCode.AI_RESPONSE_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("일기 소유자가 다른 경우, 예외 처리")
        void ai_response_forbidden() {
            User otherUser = User.create("other@naver.com", ProviderType.KAKAO, "9999999999");
            ReflectionTestUtils.setField(otherUser, "userId", 2L);
            Diary otherDiary = Diary.create(otherUser, "다른 내용", EmotionType.JOY);
            AiResponse otherAiResponse = AiResponse.create(otherDiary);

            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(otherAiResponse));

            assertThatThrownBy(() -> aiResponseService.getAiResponse(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DiaryErrorCode.DIARY_ACCESS_DENIED.getMessage());
        }

        @Test
        @DisplayName("성공")
        void success() {
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            AiResponseResponse result = aiResponseService.getAiResponse(1L, 1L);

            assertThat(result.response()).isEqualTo("테스트 AI 응답입니다.");
            assertThat(result.aiResponseStatusType()).isEqualTo(AiResponseStatusType.PENDING);
        }
    }

    @Nested
    @DisplayName("AI 응답 상태 COMPLETE로 수정")
    class MarkCompletedAndSaveResponse {

        @Test
        @DisplayName("AI 응답이 없는 경우, 예외 처리")
        void ai_response_not_found() {
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> aiResponseService.markCompletedAndSaveResponse(1L, "응답 내용"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AiResponseErrorCode.AI_RESPONSE_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("성공")
        void success() {
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            aiResponseService.markCompletedAndSaveResponse(1L, "새 AI 응답입니다.");

            assertThat(aiResponse.getContent()).isEqualTo("새 AI 응답입니다.");
            assertThat(aiResponse.getAiResponseStatusType()).isEqualTo(AiResponseStatusType.COMPLETED);
        }
    }

    @Nested
    @DisplayName("AI 응답 상태 FAILED로 수정")
    class MarkFailed {

        @Test
        @DisplayName("AI 응답이 없는 경우, 예외 처리")
        void ai_response_not_found() {
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> aiResponseService.markFailed(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AiResponseErrorCode.AI_RESPONSE_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("성공")
        void success() {
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            aiResponseService.markFailed(1L);

            assertThat(aiResponse.getAiResponseStatusType()).isEqualTo(AiResponseStatusType.FAILED);
        }

        @Test
        @DisplayName("이미 COMPLETED 상태인 경우, 상태 유지")
        void already_completed_keeps_state() {
            ReflectionTestUtils.setField(aiResponse, "aiResponseStatusType", AiResponseStatusType.COMPLETED);
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            aiResponseService.markFailed(1L);

            assertThat(aiResponse.getAiResponseStatusType()).isEqualTo(AiResponseStatusType.COMPLETED);
        }
    }
}
