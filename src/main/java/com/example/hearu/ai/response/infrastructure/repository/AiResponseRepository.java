package com.example.hearu.ai.response.infrastructure.repository;

import com.example.hearu.ai.response.domain.AiResponse;
import com.example.hearu.ai.response.domain.AiResponseStatusType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
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

    // 회수 대상: cutoff 이전부터 갱신이 없는 PENDING. 오래된 순으로 잘라 한 번의 실행이 무한정 길어지지 않게 한다.
    @Query("""
            select r.aiResponseId from AiResponse r
            where r.aiResponseStatusType = :status
              and r.updatedAt < :cutoff
              and r.deletedAt is null
            order by r.updatedAt
            """)
    List<Long> findStaleIds(
            @Param("status") AiResponseStatusType status,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable);

    // 조건부 UPDATE로 회수 대상을 선점한다. 조회 이후 완료·삭제됐거나 이미 다른 실행이 갱신했으면 0을 반환한다.
    @Modifying(clearAutomatically = true)
    @Query("""
            update AiResponse r
            set r.updatedAt = :now
            where r.aiResponseId = :id
              and r.aiResponseStatusType = :status
              and r.updatedAt < :cutoff
              and r.deletedAt is null
            """)
    int claimStale(
            @Param("id") Long id,
            @Param("status") AiResponseStatusType status,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now);
}
