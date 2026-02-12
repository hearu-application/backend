package com.example.hearu.ai.response.service;

import com.example.hearu.ai.response.domain.AiResponse;
import com.example.hearu.ai.response.domain.AiResponseErrorCode;
import com.example.hearu.ai.response.dto.response.AiResponseResponse;
import com.example.hearu.ai.response.infrastructure.repository.AiResponseRepository;
import com.example.hearu.diary.service.DiaryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hearu.common.util.exception.BusinessException;
import com.example.hearu.diary.domain.Diary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiResponseService {

    private final DiaryService diaryService;
    private final AiResponseRepository aiResponseRepository;

    @Transactional(readOnly = true)
    public AiResponseResponse getAiResponse(Long userId, Long aiResponseId) {

        // 1. AiResponse 엔티티 조회
        AiResponse aiResponse = aiResponseRepository.findById(aiResponseId)
            .orElseThrow(() -> {
                log.debug("AI 응답 데이터가 존재하지 않습니다. aiResponseId={}", aiResponseId);
                return new BusinessException(AiResponseErrorCode.AI_RESPONSE_NOT_FOUNT);
            });

        // 2. AIResponse와 연관된 일기가 인증 소유자와 같은지 검증
        aiResponse.getDiary().validateOwner(userId);

        return new AiResponseResponse(
            aiResponse.getResponse(),
            aiResponse.getAiResponseStatusType()
        );
    }

    @Transactional
    public void completeAiResponse(Long userId, Long diaryId, String response) {

        // 1. AiResponse 엔티티 조회
        AiResponse aiResponse = getAiResponseOrThrow(userId, diaryId);

        try {
            // 2. AI 응답 상태 COMPLETE 수정 & AI 응답 저장
            aiResponse.completeResponse(response);
        } catch (BusinessException e) {
            // 2. AI 응답 상태 FAILED 수정
            aiResponse.failResponse();
        } catch (Exception e) {
            // 2. AI 응답 상태 FAILED 수정
            log.warn("AI 응답 처리 중 시스템 오류. userId={}, diaryId={}", userId, diaryId, e);
            aiResponse.failResponse();
        }
    }

    @Transactional(readOnly = true)
    public AiResponse getAiResponseOrThrow(Long userId, Long diaryId) {
        // 1. Diary 엔티티 조회
        Diary diary = diaryService.getDiary(userId, diaryId);

        // 2. AiResponse 엔티티 조회
        return aiResponseRepository.findByDiary(diary)
                .orElseThrow(() -> {
                    log.warn("AI 응답 데이터가 존재하지 않습니다. userId={}, diaryId={}", userId, diaryId);
                    return new BusinessException(AiResponseErrorCode.AI_RESPONSE_NOT_FOUNT);
                });
    }

    @Transactional
    public void markFailed(Long userId, Long diaryId) {
        Diary diary = diaryService.getDiary(userId, diaryId);
        AiResponse aiResponse = aiResponseRepository.findByDiary(diary)
                .orElseThrow(() -> new BusinessException(AiResponseErrorCode.AI_RESPONSE_NOT_FOUNT));
        aiResponse.failResponse();
    }
}
