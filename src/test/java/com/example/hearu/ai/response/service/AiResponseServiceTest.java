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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.hearu.ai.character.domain.Companion;
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
import com.example.hearu.diary.service.DiaryService;
import com.example.hearu.user.domain.User;

@ExtendWith(MockitoExtension.class)
public class AiResponseServiceTest {

    @Mock
    DiaryService diaryService;

    @Mock
    AiResponseRepository aiResponseRepository;

    @InjectMocks
    AiResponseService aiResponseService;

    private Diary diary;
    private AiResponse aiResponse;

    @BeforeEach
    void setUp() {
        Companion companion = Mockito.mock(Companion.class);

        User user = User.create(
            "example@naver.com",
            ProviderType.KAKAO,
            "1234567890",
            companion
        );
        ReflectionTestUtils.setField(user, "userId", 1L);

        diary = Diary.create(user, "내용", EmotionType.JOY);
        ReflectionTestUtils.setField(diary, "diaryId", 1L);

        aiResponse = AiResponse.create(diary);
        ReflectionTestUtils.setField(aiResponse, "aiResponseId", 1L);
        ReflectionTestUtils.setField(aiResponse, "response", "테스트 내용입니다.");
    }

    @Nested
    @DisplayName("AI 응답 조회")
    class GetAiResponse {

        @Test
        @DisplayName("일기가 없는 경우, 예외 처리")
        void diary_not_found() {
            given(diaryService.getDiaryOrThrow(1L, 1L))
                .willThrow(new BusinessException(DiaryErrorCode.DIARY_NOT_FOUND));

            assertThatThrownBy(() -> aiResponseService.getAiResponse(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DiaryErrorCode.DIARY_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("AI 응답이 없는 경우, 예외 처리")
        void ai_response_not_found() {
            given(diaryService.getDiaryOrThrow(1L, 1L)).willReturn(diary);
            given(aiResponseRepository.findByDiaryAndDeletedAtIsNull(diary)).willReturn(Optional.empty());

            assertThatThrownBy(() -> aiResponseService.getAiResponse(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AiResponseErrorCode.AI_RESPONSE_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("AI 응답과 일기 소유자가 다른 경우, 예외 처리")
        void ai_response_forbidden() {
            User otherUser = User.create("other@naver.com", ProviderType.KAKAO, "9999999999", Mockito.mock(Companion.class));
            ReflectionTestUtils.setField(otherUser, "userId", 2L);
            Diary otherDiary = Diary.create(otherUser, "다른 내용", EmotionType.JOY);
            ReflectionTestUtils.setField(otherDiary, "diaryId", 2L);
            AiResponse otherAiResponse = AiResponse.create(otherDiary);

            given(diaryService.getDiaryOrThrow(1L, 1L)).willReturn(diary);
            given(aiResponseRepository.findByDiaryAndDeletedAtIsNull(diary)).willReturn(Optional.of(otherAiResponse));

            assertThatThrownBy(() -> aiResponseService.getAiResponse(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DiaryErrorCode.DIARY_ACCESS_DENIED.getMessage());
        }

        @Test
        @DisplayName("성공")
        void success() {
            given(diaryService.getDiaryOrThrow(1L, 1L)).willReturn(diary);
            given(aiResponseRepository.findByDiaryAndDeletedAtIsNull(diary)).willReturn(Optional.of(aiResponse));

            AiResponseResponse result = aiResponseService.getAiResponse(1L, 1L);

            assertThat(result.aiResponseStatusType()).isEqualTo(aiResponse.getAiResponseStatusType());
            assertThat(result.response()).isEqualTo(aiResponse.getResponse());
        }
    }

    @Nested
    @DisplayName("AI 응답 엔티티 조회")
    class GetAiResponseOrThrow {

        @Test
        @DisplayName("일기가 없는 경우, 예외 처리")
        void diary_not_found() {
            given(diaryService.getDiaryOrThrow(1L, 1L)).willThrow(new BusinessException(DiaryErrorCode.DIARY_NOT_FOUND));

            assertThatThrownBy(() -> aiResponseService.getAiResponseOrThrow(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DiaryErrorCode.DIARY_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("AI 응답이 없는 경우, 예외 처리")
        void ai_response_not_found() {
            given(diaryService.getDiaryOrThrow(1L, 1L)).willReturn(diary);
            given(aiResponseRepository.findByDiaryAndDeletedAtIsNull(diary)).willReturn(Optional.empty());

            assertThatThrownBy(() -> aiResponseService.getAiResponseOrThrow(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AiResponseErrorCode.AI_RESPONSE_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("성공")
        void success() {
            given(diaryService.getDiaryOrThrow(1L, 1L)).willReturn(diary);
            given(aiResponseRepository.findByDiaryAndDeletedAtIsNull(diary)).willReturn(Optional.of(aiResponse));

            AiResponse result = aiResponseService.getAiResponseOrThrow(1L, 1L);

            assertThat(result.getAiResponseId()).isEqualTo(aiResponse.getAiResponseId());
            assertThat(result.getResponse()).isEqualTo(aiResponse.getResponse());
            assertThat(result.getAiResponseStatusType()).isEqualTo(aiResponse.getAiResponseStatusType());
        }
    }

    @Nested
    @DisplayName("AI 응답 상태 COMPLETE로 수정")
    class AiResponseMarkedCompleted {

        @Test
        @DisplayName("일기가 없는 경우, 예외 처리")
        void diary_not_found() {
            given(diaryService.getDiaryOrThrow(1L, 1L))
                .willThrow(new BusinessException(DiaryErrorCode.DIARY_NOT_FOUND));

            assertThatThrownBy(() -> aiResponseService.markedCompletedAndSaveResponse(1L, 1L, "테스트 AI 응답 내용입니다."))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DiaryErrorCode.DIARY_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("AI 응답이 없는 경우, 예외 처리")
        void ai_response_not_found() {
            given(diaryService.getDiaryOrThrow(1L, 1L)).willReturn(diary);
            given(aiResponseRepository.findByDiaryAndDeletedAtIsNull(diary)).willReturn(Optional.empty());

            assertThatThrownBy(() -> aiResponseService.markedCompletedAndSaveResponse(1L, 1L, "테스트 AI 응답 내용입니다."))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AiResponseErrorCode.AI_RESPONSE_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("예기치 못한 오류 발생 시, 실패 처리")
        void exception_marked_failed() {
            AiResponse mockAiResponse = Mockito.mock(AiResponse.class);
            willThrow(new RuntimeException("AI 처리 실패"))
                .given(mockAiResponse).completeResponse(any());
            given(diaryService.getDiaryOrThrow(1L, 1L)).willReturn(diary);
            given(aiResponseRepository.findByDiaryAndDeletedAtIsNull(diary)).willReturn(Optional.of(mockAiResponse));

            aiResponseService.markedCompletedAndSaveResponse(1L, 1L, "테스트 AI 응답 내용입니다.");

            verify(mockAiResponse, times(1)).completeResponse("테스트 AI 응답 내용입니다.");
            verify(mockAiResponse, times(1)).failResponse();
        }

        @Test
        @DisplayName("성공")
        void success() {
            given(diaryService.getDiaryOrThrow(1L, 1L)).willReturn(diary);
            given(aiResponseRepository.findByDiaryAndDeletedAtIsNull(diary)).willReturn(Optional.of(aiResponse));

            aiResponseService.markedCompletedAndSaveResponse(1L, 1L, "테스트 AI 응답 내용입니다.");

            assertThat(aiResponse.getResponse()).isEqualTo("테스트 AI 응답 내용입니다.");
            assertThat(aiResponse.getAiResponseStatusType()).isEqualTo(AiResponseStatusType.COMPLETED);
        }
    }

    @Nested
    @DisplayName("AI 응답 상태 FAILED로 수정")
    class AiResponseMarkedFailed {

        @Test
        @DisplayName("일기가 없는 경우, 예외 처리")
        void diary_not_found() {
            given(diaryService.getDiaryOrThrow(1L, 1L))
                .willThrow(new BusinessException(DiaryErrorCode.DIARY_NOT_FOUND));

            assertThatThrownBy(() -> aiResponseService.markFailed(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DiaryErrorCode.DIARY_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("AI 응답이 없는 경우, 예외 처리")
        void ai_response_not_found() {
            given(diaryService.getDiaryOrThrow(1L, 1L)).willReturn(diary);
            given(aiResponseRepository.findByDiaryAndDeletedAtIsNull(diary)).willReturn(Optional.empty());

            assertThatThrownBy(() -> aiResponseService.markFailed(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AiResponseErrorCode.AI_RESPONSE_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("성공")
        void success() {
            given(diaryService.getDiaryOrThrow(1L, 1L)).willReturn(diary);
            given(aiResponseRepository.findByDiaryAndDeletedAtIsNull(diary)).willReturn(Optional.of(aiResponse));

            aiResponseService.markFailed(1L, 1L);

            assertThat(aiResponse.getAiResponseStatusType()).isEqualTo(AiResponseStatusType.FAILED);
        }
    }
}
