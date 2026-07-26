package com.example.hearu.ai.feedback.infrastructure.repository;

import com.example.hearu.ai.feedback.domain.AiFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiFeedbackRepository extends JpaRepository<AiFeedback, Long> {
    Optional<AiFeedback> findByAiResponse_AiResponseIdAndDeletedAtIsNull(Long aiResponseId);

    // 일기 삭제 전파용. AI 응답 하나에 피드백 하나이므로 단건으로 조회한다.
    Optional<AiFeedback> findByAiResponse_Diary_DiaryIdAndDeletedAtIsNull(Long diaryId);
}
