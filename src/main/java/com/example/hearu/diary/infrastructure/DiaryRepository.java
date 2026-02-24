package com.example.hearu.diary.infrastructure;

import com.example.hearu.diary.domain.Diary;

import com.example.hearu.user.domain.User;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DiaryRepository extends JpaRepository<Diary, Long> {

    List<Diary> findByUserAndCreatedAtBetweenOrderByCreatedAtDesc(User user, LocalDateTime start, LocalDateTime end);

    Optional<Diary> findByDiaryIdAndDeletedAtIsNull(Long id);

    List<Diary> findByUser_UserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}