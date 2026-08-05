package com.example.hearu.diary.infrastructure;

import com.example.hearu.diary.domain.Diary;
import com.example.hearu.diary.dto.response.DiaryDetailResponse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiaryRepository extends JpaRepository<Diary, Long> {

    // DTO 프로젝션으로 조회한다. 엔티티를 로딩하지 않으므로 Diary의 EAGER @OneToOne(aiResponse)이
    // 초기화되지 않아, 목록 건수만큼 ai_response SELECT가 나가는 N+1이 발생하지 않는다.
    // 기간은 반열린 구간(start <= createdAt < end)으로 조회한다.
    @Query("""
            select new com.example.hearu.diary.dto.response.DiaryDetailResponse(
                d.diaryId, d.content, d.emotionType, d.createdAt, d.updatedAt)
            from Diary d
            where d.user.userId = :userId
              and d.deletedAt is null
              and d.createdAt >= :start
              and d.createdAt < :end
            order by d.createdAt desc
            """)
    List<DiaryDetailResponse> findCalendarDiaries(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    Optional<Diary> findByDiaryIdAndDeletedAtIsNull(Long id);

    int countAllByUser_UserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId, LocalDateTime start, LocalDateTime end);
}