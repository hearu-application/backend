package com.example.hearu.ai.response.service;

import static org.assertj.core.api.AssertionsForInterfaceTypes.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

        Diary diary = Diary.create(user, "내용", EmotionType.JOY, LocalDate.now());
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
            Diary otherDiary = Diary.create(otherUser, "다른 내용", EmotionType.JOY, LocalDate.now());
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

    @Nested
    @DisplayName("AI 응답 재요청 시 상태 PENDING으로 초기화")
    class MarkPending {

        @Test
        @DisplayName("AI 응답이 없는 경우, 예외 처리")
        void ai_response_not_found() {
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> aiResponseService.markPending(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AiResponseErrorCode.AI_RESPONSE_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("이미 COMPLETED 상태인 경우, 재요청 거부")
        void already_completed_rejected() {
            ReflectionTestUtils.setField(aiResponse, "aiResponseStatusType", AiResponseStatusType.COMPLETED);
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            assertThatThrownBy(() -> aiResponseService.markPending(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AiResponseErrorCode.AI_RESPONSE_ALREADY_COMPLETED.getMessage());

            assertThat(aiResponse.getAiResponseStatusType()).isEqualTo(AiResponseStatusType.COMPLETED);
        }

        @Test
        @DisplayName("FAILED 상태인 경우, PENDING으로 초기화")
        void failed_to_pending() {
            ReflectionTestUtils.setField(aiResponse, "aiResponseStatusType", AiResponseStatusType.FAILED);
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            aiResponseService.markPending(1L);

            assertThat(aiResponse.getAiResponseStatusType()).isEqualTo(AiResponseStatusType.PENDING);
        }

        @Test
        @DisplayName("이미 PENDING 상태인 경우, 예외 없이 PENDING 유지")
        void already_pending_kept() {
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            aiResponseService.markPending(1L);

            assertThat(aiResponse.getAiResponseStatusType()).isEqualTo(AiResponseStatusType.PENDING);
        }
    }

    @Nested
    @DisplayName("PENDING AI 응답 생성")
    class CreatePending {

        @Test
        @DisplayName("성공 - PENDING 상태로 저장")
        void success() {
            User user = User.create("example@naver.com", ProviderType.KAKAO, "1234567890");
            Diary diary = Diary.create(user, "내용", EmotionType.JOY, LocalDate.now());
            ReflectionTestUtils.setField(diary, "diaryId", 1L);

            aiResponseService.createPending(diary);

            ArgumentCaptor<AiResponse> captor = ArgumentCaptor.forClass(AiResponse.class);
            verify(aiResponseRepository).save(captor.capture());
            assertThat(captor.getValue().getAiResponseStatusType()).isEqualTo(AiResponseStatusType.PENDING);
            assertThat(captor.getValue().getDiary()).isEqualTo(diary);
        }
    }

    @Nested
    @DisplayName("AI 응답 soft delete")
    class SoftDeleteByDiaryId {

        @Test
        @DisplayName("존재하면 soft delete 한다")
        void success() {
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            aiResponseService.softDeleteByDiaryId(1L);

            assertThat(aiResponse.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않아도 예외 없이 넘어간다")
        void skip_when_absent() {
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

            aiResponseService.softDeleteByDiaryId(1L);

            assertThat(aiResponse.getDeletedAt()).isNull();
        }
    }
}
