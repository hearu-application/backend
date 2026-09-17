package com.example.hearu.diary.infrastructure;

import com.example.hearu.diary.domain.Diary;
import com.example.hearu.diary.dto.response.DiaryDetailResponse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiaryRepository extends JpaRepository<Diary, Long> {

    // DTO 프로젝션으로 조회한다. 엔티티를 로딩하지 않으므로 Diary의 EAGER @OneToOne(aiResponse)이
    // 초기화되지 않아, 목록 건수만큼 ai_response SELECT가 나가는 N+1이 발생하지 않는다.
    // 캘린더는 대상 날짜(diaryDate) 기준으로 조회한다. 기간은 반열린 구간(start <= diaryDate < end).
    @Query("""
            select new com.example.hearu.diary.dto.response.DiaryDetailResponse(
                d.diaryId, d.content, d.emotionType, d.diaryDate, d.createdAt, d.updatedAt)
            from Diary d
            where d.user.userId = :userId
              and d.deletedAt is null
              and d.diaryDate >= :start
              and d.diaryDate < :end
            order by d.diaryDate desc, d.createdAt desc
            """)
    List<DiaryDetailResponse> findCalendarDiaries(
            @Param("userId") Long userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    Optional<Diary> findByDiaryIdAndDeletedAtIsNull(Long id);

    int countAllByUser_UserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId, LocalDateTime start, LocalDateTime end);
}