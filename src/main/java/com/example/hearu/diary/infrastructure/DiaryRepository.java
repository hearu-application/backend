package com.example.hearu.diary.infrastructure;

import com.example.hearu.diary.domain.Diary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiaryRepository extends JpaRepository<Diary, Long> {
}