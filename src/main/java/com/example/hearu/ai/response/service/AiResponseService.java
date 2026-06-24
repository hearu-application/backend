package com.example.hearu.ai.response.service;

import com.example.hearu.ai.response.domain.AiResponse;
import com.example.hearu.ai.response.domain.AiResponseErrorCode;
import com.example.hearu.ai.response.dto.response.AiResponseResponse;
import com.example.hearu.ai.response.infrastructure.repository.AiResponseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hearu.common.util.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AiResponseService {

    private final AiResponseRepository aiResponseRepository;

    @Transactional(readOnly = true)
    public AiResponseResponse getAiResponse(Long userId, Long diaryId) {
        AiResponse aiResponse = getAiResponseOrThrow(diaryId);
        aiResponse.getDiary().validateOwner(userId);
        return new AiResponseResponse(aiResponse.getContent(), aiResponse.getAiResponseStatusType());
    }

    @Transactional(readOnly = true)
    public AiResponse getOwnedAiResponse(Long userId, Long diaryId) {
        AiResponse aiResponse = getAiResponseOrThrow(diaryId);
        aiResponse.getDiary().validateOwner(userId);
        return aiResponse;
    }

    public void markCompletedAndSaveResponse(Long diaryId, String content) {
        AiResponse aiResponse = getAiResponseOrThrow(diaryId);
        aiResponse.completeResponse(content);
    }

    public void markFailed(Long diaryId) {
        AiResponse aiResponse = getAiResponseOrThrow(diaryId);
        aiResponse.failResponse();
    }

    private AiResponse getAiResponseOrThrow(Long diaryId) {
        return aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(diaryId)
            .orElseThrow(() -> {
                log.warn("AI 응답 데이터가 존재하지 않습니다. diaryId={}", diaryId);
                return new BusinessException(AiResponseErrorCode.AI_RESPONSE_NOT_FOUND);
            });
    }
}
