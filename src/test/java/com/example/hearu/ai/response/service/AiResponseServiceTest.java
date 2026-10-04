package com.example.hearu.ai.response.service;

import static org.assertj.core.api.AssertionsForInterfaceTypes.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.hearu.ai.response.domain.AiResponse;
import com.example.hearu.ai.response.domain.AiResponseErrorCode;
import com.example.hearu.ai.response.domain.AiResponseStatusType;
import com.example.hearu.ai.response.dto.response.AiResponseResponse;
import com.example.hearu.ai.response.infrastructure.repository.AiResponseRepository;
import com.example.hearu.ai.response.domain.AttemptStartResult;
import com.example.hearu.ai.response.service.AiResponseService.AttemptStart;
import com.example.hearu.ai.response.service.AiResponseService.FailedAttempt;
import com.example.hearu.auth.domain.ProviderType;
import com.example.hearu.common.util.exception.BusinessException;
import com.example.hearu.diary.domain.Diary;
import com.example.hearu.diary.domain.EmotionType;
import com.example.hearu.diary.domain.error.DiaryErrorCode;
import com.example.hearu.diary.event.DiaryAiResponseRequestedEvent;
import com.example.hearu.user.domain.User;

@ExtendWith(MockitoExtension.class)
public class AiResponseServiceTest {

    @Mock
    AiResponseRepository aiResponseRepository;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

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
    @DisplayName("실행 시작 (횟수는 시작 시점에 센다)")
    class StartAttempt {

        @Test
        @DisplayName("AI 응답이 없는 경우, 예외 처리")
        void ai_response_not_found() {
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> aiResponseService.startAttempt(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AiResponseErrorCode.AI_RESPONSE_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("상한 전이면 횟수를 올리고 STARTED")
        void below_limit_started() {
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            AttemptStart result = aiResponseService.startAttempt(1L);

            assertThat(result.result()).isEqualTo(AttemptStartResult.STARTED);
            assertThat(result.attemptCount()).isEqualTo(1);
            assertThat(aiResponse.getAiResponseStatusType()).isEqualTo(AiResponseStatusType.PENDING);
        }

        // 실패 기록 없이 끝난 실행이 반복돼도(프로세스 종료 등) 상한을 넘겨 LLM을 부르지 않는다.
        @Test
        @DisplayName("상한을 다 썼으면 횟수를 올리지 않고 FAILED로 확정하고 EXHAUSTED")
        void at_limit_exhausted() {
            ReflectionTestUtils.setField(aiResponse, "attemptCount", AiResponseService.MAX_EXECUTIONS);
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            AttemptStart result = aiResponseService.startAttempt(1L);

            assertThat(result.result()).isEqualTo(AttemptStartResult.EXHAUSTED);
            assertThat(aiResponse.getAttemptCount()).isEqualTo(AiResponseService.MAX_EXECUTIONS);
            assertThat(aiResponse.getAiResponseStatusType()).isEqualTo(AiResponseStatusType.FAILED);
        }

        @Test
        @DisplayName("이미 COMPLETED면 횟수를 올리지 않고 SKIPPED")
        void completed_skipped() {
            ReflectionTestUtils.setField(aiResponse, "aiResponseStatusType", AiResponseStatusType.COMPLETED);
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            AttemptStart result = aiResponseService.startAttempt(1L);

            assertThat(result.result()).isEqualTo(AttemptStartResult.SKIPPED);
            assertThat(aiResponse.getAttemptCount()).isZero();
        }

        @Test
        @DisplayName("이미 FAILED면 횟수를 올리지 않고 SKIPPED")
        void failed_skipped() {
            ReflectionTestUtils.setField(aiResponse, "aiResponseStatusType", AiResponseStatusType.FAILED);
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            AttemptStart result = aiResponseService.startAttempt(1L);

            assertThat(result.result()).isEqualTo(AttemptStartResult.SKIPPED);
            assertThat(aiResponse.getAiResponseStatusType()).isEqualTo(AiResponseStatusType.FAILED);
        }
    }

    @Nested
    @DisplayName("실행 실패 기록")
    class RecordFailure {

        @Test
        @DisplayName("AI 응답이 없는 경우, 예외 처리")
        void ai_response_not_found() {
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> aiResponseService.recordFailure(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AiResponseErrorCode.AI_RESPONSE_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("상한 전이면 횟수를 더 올리지 않고 PENDING을 유지한다")
        void below_limit_keeps_pending() {
            ReflectionTestUtils.setField(aiResponse, "attemptCount", 1);
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            FailedAttempt result = aiResponseService.recordFailure(1L);

            assertThat(result.finalFailure()).isFalse();
            assertThat(aiResponse.getAttemptCount()).isEqualTo(1);
            assertThat(aiResponse.getAiResponseStatusType()).isEqualTo(AiResponseStatusType.PENDING);
        }

        @Test
        @DisplayName("상한(3회)째 실행이 실패하면 FAILED로 확정한다")
        void at_limit_marks_failed() {
            ReflectionTestUtils.setField(aiResponse, "attemptCount", AiResponseService.MAX_EXECUTIONS);
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            FailedAttempt result = aiResponseService.recordFailure(1L);

            assertThat(result.finalFailure()).isTrue();
            assertThat(result.attemptCount()).isEqualTo(AiResponseService.MAX_EXECUTIONS);
            assertThat(aiResponse.getAiResponseStatusType()).isEqualTo(AiResponseStatusType.FAILED);
        }

        @Test
        @DisplayName("이미 COMPLETED 상태인 경우, 상태를 유지한다")
        void already_completed_keeps_state() {
            ReflectionTestUtils.setField(aiResponse, "aiResponseStatusType", AiResponseStatusType.COMPLETED);
            ReflectionTestUtils.setField(aiResponse, "attemptCount", AiResponseService.MAX_EXECUTIONS);
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            FailedAttempt result = aiResponseService.recordFailure(1L);

            assertThat(result.finalFailure()).isFalse();
            assertThat(aiResponse.getAiResponseStatusType()).isEqualTo(AiResponseStatusType.COMPLETED);
        }
    }

    @Nested
    @DisplayName("AI 응답 재요청 (FAILED일 때만)")
    class RequestRetryIfFailed {

        @Test
        @DisplayName("AI 응답이 없는 경우, 예외 처리")
        void ai_response_not_found() {
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> aiResponseService.requestRetryIfFailed(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AiResponseErrorCode.AI_RESPONSE_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("COMPLETED 상태인 경우, 아무것도 하지 않고 false")
        void completed_is_noop() {
            ReflectionTestUtils.setField(aiResponse, "aiResponseStatusType", AiResponseStatusType.COMPLETED);
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            boolean result = aiResponseService.requestRetryIfFailed(1L);

            assertThat(result).isFalse();
            assertThat(aiResponse.getAiResponseStatusType()).isEqualTo(AiResponseStatusType.COMPLETED);
        }

        @Test
        @DisplayName("PENDING 상태인 경우, 아무것도 하지 않고 false")
        void pending_is_noop() {
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            boolean result = aiResponseService.requestRetryIfFailed(1L);

            assertThat(result).isFalse();
            assertThat(aiResponse.getAiResponseStatusType()).isEqualTo(AiResponseStatusType.PENDING);
        }

        @Test
        @DisplayName("FAILED 상태인 경우, PENDING으로 되돌리고 실행 1회만 남긴다")
        void failed_to_pending_with_one_execution_left() {
            ReflectionTestUtils.setField(aiResponse, "aiResponseStatusType", AiResponseStatusType.FAILED);
            ReflectionTestUtils.setField(aiResponse, "attemptCount", AiResponseService.MAX_EXECUTIONS);
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            boolean result = aiResponseService.requestRetryIfFailed(1L);

            assertThat(result).isTrue();
            assertThat(aiResponse.getAiResponseStatusType()).isEqualTo(AiResponseStatusType.PENDING);
            assertThat(aiResponse.getAttemptCount()).isEqualTo(AiResponseService.MAX_EXECUTIONS - 1);
        }

        // 재요청 → 시작 → 실패가 한 사이클로 "1회 실행 후 곧바로 FAILED"가 되는지 도메인 전이를 이어서 확인한다.
        @Test
        @DisplayName("재요청 후 실행이 다시 실패하면 회수 없이 곧바로 FAILED가 된다")
        void retry_then_failure_is_final() {
            ReflectionTestUtils.setField(aiResponse, "aiResponseStatusType", AiResponseStatusType.FAILED);
            given(aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(aiResponse));

            aiResponseService.requestRetryIfFailed(1L);
            AttemptStart start = aiResponseService.startAttempt(1L);
            FailedAttempt failure = aiResponseService.recordFailure(1L);

            assertThat(start.result()).isEqualTo(AttemptStartResult.STARTED);
            assertThat(failure.finalFailure()).isTrue();
            assertThat(aiResponse.getAiResponseStatusType()).isEqualTo(AiResponseStatusType.FAILED);
        }
    }

    @Nested
    @DisplayName("고착된 PENDING 선점 및 재발행")
    class ClaimAndRepublish {

        private final LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);

        @Test
        @DisplayName("선점에 실패하면 이벤트를 발행하지 않고 false")
        void claim_failed() {
            given(aiResponseRepository.claimStale(eq(1L), eq(AiResponseStatusType.PENDING), eq(cutoff), any()))
                .willReturn(0);

            boolean result = aiResponseService.claimAndRepublish(1L, cutoff);

            assertThat(result).isFalse();
            verify(aiResponseRepository, never()).findById(anyLong());
            verifyNoInteractions(applicationEventPublisher);
        }

        @Test
        @DisplayName("선점에 성공하면 엔티티 값으로 이벤트를 발행하고 true")
        void claim_succeeded() {
            given(aiResponseRepository.claimStale(eq(1L), eq(AiResponseStatusType.PENDING), eq(cutoff), any()))
                .willReturn(1);
            given(aiResponseRepository.findById(1L)).willReturn(Optional.of(aiResponse));

            boolean result = aiResponseService.claimAndRepublish(1L, cutoff);

            assertThat(result).isTrue();
            ArgumentCaptor<DiaryAiResponseRequestedEvent> captor =
                ArgumentCaptor.forClass(DiaryAiResponseRequestedEvent.class);
            verify(applicationEventPublisher).publishEvent(captor.capture());

            Diary diary = aiResponse.getDiary();
            DiaryAiResponseRequestedEvent event = captor.getValue();
            assertThat(event.diaryId()).isEqualTo(diary.getDiaryId());
            assertThat(event.content()).isEqualTo(diary.getContent());
            assertThat(event.emotionType()).isEqualTo(diary.getEmotionType());
            assertThat(event.userId()).isEqualTo(diary.getUser().getUserId());
            assertThat(event.nickname()).isEqualTo(diary.getUser().getNickname());
            assertThat(event.toneType()).isEqualTo(diary.getUser().getToneType());
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
