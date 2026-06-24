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

    public void createFeedback(Long userId, Long diaryId, AiFeedbackCreateRequest request) {
        AiResponse aiResponse = aiResponseService.getOwnedAiResponse(userId, diaryId);

        aiFeedbackRepository.findByAiResponse_AiResponseIdAndDeletedAtIsNull(aiResponse.getAiResponseId())
            .ifPresentOrElse(
                feedback -> feedback.update(
                    request.feedbackType(),
                    request.dislikeReasonType(),
                    request.customText()
                ),
                () -> aiFeedbackRepository.save(AiFeedback.create(
                    aiResponse,
                    request.feedbackType(),
                    request.dislikeReasonType(),
                    request.customText()
                ))
            );
    }
}
