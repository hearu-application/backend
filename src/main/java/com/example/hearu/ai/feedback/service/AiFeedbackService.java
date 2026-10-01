package com.example.hearu.ai.feedback.service;

import com.example.hearu.ai.feedback.domain.AiFeedback;
import com.example.hearu.ai.feedback.dto.request.AiFeedbackCreateRequest;
import com.example.hearu.ai.feedback.infrastructure.repository.AiFeedbackRepository;
import com.example.hearu.ai.response.domain.AiResponse;
import com.example.hearu.ai.response.service.AiResponseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AiFeedbackService {

    private final AiFeedbackRepository aiFeedbackRepository;
    private final AiResponseService aiResponseService;

    // 일기 soft delete에 맞춰 피드백도 soft delete한다. 피드백은 남기지 않은 경우가 흔하므로
    // 존재하지 않으면 예외를 던지지 않고 건너뛴다. (AiResponseService.softDeleteByDiaryId와 동일한 규약)
    public void softDeleteByDiaryId(Long diaryId) {
        aiFeedbackRepository.findByAiResponse_Diary_DiaryIdAndDeletedAtIsNull(diaryId)
            .ifPresentOrElse(
                AiFeedback::softDelete,
                () -> log.debug("삭제할 AI 피드백이 없어 건너뜀. diaryId={}", diaryId)
            );
    }

    public int hardDeleteAllByUserId(Long userId) {
        return aiFeedbackRepository.deleteAllByUserId(userId);
    }

    public void createFeedback(Long userId, Long diaryId, AiFeedbackCreateRequest request) {
        AiResponse aiResponse = aiResponseService.getOwnedAiResponse(userId, diaryId);

        aiFeedbackRepository.findByAiResponse_AiResponseIdAndDeletedAtIsNull(aiResponse.getAiResponseId())
            .ifPresentOrElse(
                feedback -> {
                    log.debug("기존 피드백 갱신. userId={}, diaryId={}, aiResponseId={}, feedbackType={}",
                        userId, diaryId, aiResponse.getAiResponseId(), request.feedbackType());
                    feedback.update(
                        request.feedbackType(),
                        request.dislikeReasonType(),
                        request.customText()
                    );
                },
                () -> {
                    log.debug("신규 피드백 생성. userId={}, diaryId={}, aiResponseId={}, feedbackType={}",
                        userId, diaryId, aiResponse.getAiResponseId(), request.feedbackType());
                    aiFeedbackRepository.save(AiFeedback.create(
                        aiResponse,
                        request.feedbackType(),
                        request.dislikeReasonType(),
                        request.customText()
                    ));
                }
            );
    }
}
