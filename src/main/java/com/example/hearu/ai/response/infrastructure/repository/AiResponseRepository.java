package com.example.hearu.ai.response.infrastructure.repository;

import com.example.hearu.ai.response.domain.AiResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AiResponseRepository extends JpaRepository<AiResponse, Long> {
    Optional<AiResponse> findByDiary_DiaryIdAndDeletedAtIsNull(Long diaryId);

    // 탈퇴 유저 하드 삭제용. soft delete 여부와 관계없이 사용자의 AI 응답을 전부 지운다.
    @Modifying
    @Query("""
            delete from AiResponse r
            where r.diary.diaryId in (
                select d.diaryId from Diary d where d.user.userId = :userId)
            """)
    int deleteAllByUserId(@Param("userId") Long userId);
}
