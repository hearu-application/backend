package com.example.hearu.ai.response.service;

import com.example.hearu.ai.response.domain.AiResponse;
import com.example.hearu.ai.response.domain.AiResponseErrorCode;
import com.example.hearu.ai.response.dto.response.AiResponseResponse;
import com.example.hearu.ai.response.infrastructure.repository.AiResponseRepository;
import com.example.hearu.diary.domain.Diary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hearu.common.logging.LogMasker;
import com.example.hearu.common.util.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AiResponseService {

    private final AiResponseRepository aiResponseRepository;

    // 일기 생성 시 PENDING 상태의 AI 응답을 함께 만든다. (과거에는 Diary의 cascade로 생성했으나,
    // 역방향 @OneToOne 매핑을 제거하면서 생성 책임을 이쪽으로 옮겼다)
    public void createPending(Diary diary) {
        aiResponseRepository.save(AiResponse.create(diary));
        log.debug("AI 응답(PENDING) 생성. diaryId={}", diary.getDiaryId());
    }

    // 일기 soft delete에 맞춰 AI 응답도 soft delete한다. AI 응답이 없어도 삭제 자체는 성공해야 하므로
    // 존재하지 않으면 예외를 던지지 않고 건너뛴다.
    public void softDeleteByDiaryId(Long diaryId) {
        aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(diaryId)
            .ifPresentOrElse(
                AiResponse::softDelete,
                () -> log.debug("삭제할 AI 응답이 없어 건너뜀. diaryId={}", diaryId)
            );
    }

    @Transactional(readOnly = true)
    public AiResponseResponse getAiResponse(Long userId, Long diaryId) {
        AiResponse aiResponse = getAiResponseOrThrow(diaryId);
        aiResponse.getDiary().validateOwner(userId);

        // 응답이 아직 PENDING인지 FAILED인지가 클라이언트 폴링 디버깅의 핵심 정보다.
        log.debug("AI 응답 조회. diaryId={}, status={}, content={}",
            diaryId, aiResponse.getAiResponseStatusType(), LogMasker.textLength(aiResponse.getContent()));

        return new AiResponseResponse(aiResponse.getContent(), aiResponse.getAiResponseStatusType());
    }

    @Transactional(readOnly = true)
    public AiResponse getOwnedAiResponse(Long userId, Long diaryId) {
        AiResponse aiResponse = getAiResponseOrThrow(diaryId);
        aiResponse.getDiary().validateOwner(userId);
        return aiResponse;
    }

    // 호출부(AiResponseCaller)가 [AI][Complete] / 실패 사유를 이미 기록하므로 여기서는 로깅하지 않는다.
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
