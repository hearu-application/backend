package com.example.hearu.ai.feedback.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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

import com.example.hearu.ai.feedback.domain.AiFeedback;
import com.example.hearu.ai.feedback.domain.AiFeedbackErrorCode;
import com.example.hearu.ai.feedback.domain.DislikeReasonType;
import com.example.hearu.ai.feedback.domain.FeedbackType;
import com.example.hearu.ai.feedback.dto.request.AiFeedbackCreateRequest;
import com.example.hearu.ai.feedback.infrastructure.repository.AiFeedbackRepository;
import com.example.hearu.ai.response.domain.AiResponse;
import com.example.hearu.ai.response.service.AiResponseService;
import com.example.hearu.auth.domain.ProviderType;
import com.example.hearu.common.util.exception.BusinessException;
import com.example.hearu.diary.domain.Diary;
import com.example.hearu.diary.domain.EmotionType;
import com.example.hearu.user.domain.User;

@ExtendWith(MockitoExtension.class)
public class AiFeedbackServiceTest {

    @Mock
    AiFeedbackRepository aiFeedbackRepository;

    @Mock
    AiResponseService aiResponseService;

    @InjectMocks
    AiFeedbackService aiFeedbackService;

    private AiResponse aiResponse;

    @BeforeEach
    void setUp() {
        User user = User.create("example@naver.com", ProviderType.KAKAO, "1234567890");
        ReflectionTestUtils.setField(user, "userId", 1L);

        Diary diary = Diary.create(user, "내용", EmotionType.JOY);
        ReflectionTestUtils.setField(diary, "diaryId", 1L);

        aiResponse = AiResponse.create(diary);
        ReflectionTestUtils.setField(aiResponse, "aiResponseId", 1L);
        ReflectionTestUtils.setField(aiResponse, "content", "테스트 AI 응답입니다.");
    }

    @Nested
    @DisplayName("피드백 등록 - 신규 생성")
    class CreateNewFeedback {

        @Test
        @DisplayName("LIKE 등록 성공 - 사유/텍스트는 무시되어 null")
        void like_success() {
            given(aiResponseService.getOwnedAiResponse(1L, 1L)).willReturn(aiResponse);
            given(aiFeedbackRepository.findByAiResponse_AiResponseIdAndDeletedAtIsNull(1L))
                .willReturn(Optional.empty());

            AiFeedbackCreateRequest request =
                new AiFeedbackCreateRequest(FeedbackType.LIKE, DislikeReasonType.ETC, "무시될 텍스트");

            aiFeedbackService.createFeedback(1L, 1L, request);

            then(aiFeedbackRepository).should().save(argThat(feedback ->
                feedback.getFeedbackType() == FeedbackType.LIKE
                    && feedback.getDislikeReasonType() == null
                    && feedback.getCustomText() == null
            ));
        }

        @Test
        @DisplayName("DISLIKE - 사유 없이 등록 성공")
        void dislike_without_reason_success() {
            given(aiResponseService.getOwnedAiResponse(1L, 1L)).willReturn(aiResponse);
            given(aiFeedbackRepository.findByAiResponse_AiResponseIdAndDeletedAtIsNull(1L))
                .willReturn(Optional.empty());

            AiFeedbackCreateRequest request =
                new AiFeedbackCreateRequest(FeedbackType.DISLIKE, null, null);

            aiFeedbackService.createFeedback(1L, 1L, request);

            then(aiFeedbackRepository).should().save(argThat(feedback ->
                feedback.getFeedbackType() == FeedbackType.DISLIKE
                    && feedback.getDislikeReasonType() == null
                    && feedback.getCustomText() == null
            ));
        }

        @Test
        @DisplayName("DISLIKE - ETC + customText 등록 성공")
        void dislike_etc_with_text_success() {
            given(aiResponseService.getOwnedAiResponse(1L, 1L)).willReturn(aiResponse);
            given(aiFeedbackRepository.findByAiResponse_AiResponseIdAndDeletedAtIsNull(1L))
                .willReturn(Optional.empty());

            AiFeedbackCreateRequest request =
                new AiFeedbackCreateRequest(FeedbackType.DISLIKE, DislikeReasonType.ETC, "직접 입력한 사유");

            aiFeedbackService.createFeedback(1L, 1L, request);

            then(aiFeedbackRepository).should().save(argThat(feedback ->
                feedback.getDislikeReasonType() == DislikeReasonType.ETC
                    && "직접 입력한 사유".equals(feedback.getCustomText())
            ));
        }

        @Test
        @DisplayName("DISLIKE - 정해진 사유 선택 시 customText는 무시되어 null")
        void dislike_fixed_reason_ignores_text() {
            given(aiResponseService.getOwnedAiResponse(1L, 1L)).willReturn(aiResponse);
            given(aiFeedbackRepository.findByAiResponse_AiResponseIdAndDeletedAtIsNull(1L))
                .willReturn(Optional.empty());

            AiFeedbackCreateRequest request =
                new AiFeedbackCreateRequest(FeedbackType.DISLIKE, DislikeReasonType.TOO_FORMAL, "무시될 텍스트");

            aiFeedbackService.createFeedback(1L, 1L, request);

            then(aiFeedbackRepository).should().save(argThat(feedback ->
                feedback.getDislikeReasonType() == DislikeReasonType.TOO_FORMAL
                    && feedback.getCustomText() == null
            ));
        }

        @Test
        @DisplayName("DISLIKE - ETC인데 customText 누락 시 예외")
        void dislike_etc_without_text_throws() {
            given(aiResponseService.getOwnedAiResponse(1L, 1L)).willReturn(aiResponse);
            given(aiFeedbackRepository.findByAiResponse_AiResponseIdAndDeletedAtIsNull(1L))
                .willReturn(Optional.empty());

            AiFeedbackCreateRequest request =
                new AiFeedbackCreateRequest(FeedbackType.DISLIKE, DislikeReasonType.ETC, "   ");

            assertThatThrownBy(() -> aiFeedbackService.createFeedback(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AiFeedbackErrorCode.REASON_TEXT_REQUIRED.getMessage());

            then(aiFeedbackRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("피드백 등록 - 기존 행 덮어쓰기")
    class OverwriteFeedback {

        @Test
        @DisplayName("이미 피드백이 있으면 기존 행을 업데이트하고 save는 호출하지 않는다")
        void overwrite_updates_existing() {
            AiFeedback existing = AiFeedback.create(aiResponse, FeedbackType.LIKE, null, null);
            given(aiResponseService.getOwnedAiResponse(1L, 1L)).willReturn(aiResponse);
            given(aiFeedbackRepository.findByAiResponse_AiResponseIdAndDeletedAtIsNull(1L))
                .willReturn(Optional.of(existing));

            AiFeedbackCreateRequest request =
                new AiFeedbackCreateRequest(FeedbackType.DISLIKE, DislikeReasonType.TOO_AI_LIKE, null);

            aiFeedbackService.createFeedback(1L, 1L, request);

            assertThat(existing.getFeedbackType()).isEqualTo(FeedbackType.DISLIKE);
            assertThat(existing.getDislikeReasonType()).isEqualTo(DislikeReasonType.TOO_AI_LIKE);
            then(aiFeedbackRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("AI 피드백 soft delete")
    class SoftDeleteByDiaryId {

        @Test
        @DisplayName("존재하면 soft delete 한다")
        void success() {
            AiFeedback feedback = AiFeedback.create(aiResponse, FeedbackType.LIKE, null, null);
            given(aiFeedbackRepository.findByAiResponse_Diary_DiaryIdAndDeletedAtIsNull(1L))
                .willReturn(Optional.of(feedback));

            aiFeedbackService.softDeleteByDiaryId(1L);

            assertThat(feedback.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않아도 예외 없이 넘어간다")
        void skip_when_absent() {
            given(aiFeedbackRepository.findByAiResponse_Diary_DiaryIdAndDeletedAtIsNull(1L))
                .willReturn(Optional.empty());

            assertThatNoException()
                .isThrownBy(() -> aiFeedbackService.softDeleteByDiaryId(1L));
        }
    }
}
