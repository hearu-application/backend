package com.example.hearu.ai.feedback.infrastructure.repository;

import com.example.hearu.ai.feedback.domain.AiFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AiFeedbackRepository extends JpaRepository<AiFeedback, Long> {
    Optional<AiFeedback> findByAiResponse_AiResponseIdAndDeletedAtIsNull(Long aiResponseId);

    // 일기 삭제 전파용. AI 응답 하나에 피드백 하나이므로 단건으로 조회한다.
    Optional<AiFeedback> findByAiResponse_Diary_DiaryIdAndDeletedAtIsNull(Long diaryId);

    // 탈퇴 유저 하드 삭제용. soft delete 여부와 관계없이 사용자의 피드백을 전부 지운다.
    @Modifying
    @Query("""
            delete from AiFeedback f
            where f.aiResponse.aiResponseId in (
                select r.aiResponseId from AiResponse r where r.diary.user.userId = :userId)
            """)
    int deleteAllByUserId(@Param("userId") Long userId);
}
