package com.example.hearu.diary.infrastructure;

import com.example.hearu.diary.domain.Diary;
import com.example.hearu.diary.dto.response.DiaryDetailResponse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiaryRepository extends JpaRepository<Diary, Long> {

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

    // 탈퇴 유저 하드 삭제용. soft delete 여부와 관계없이 사용자의 일기를 전부 지운다.
    @Modifying
    @Query("delete from Diary d where d.user.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}