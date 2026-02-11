package com.example.hearu.ai.response.infrastructure.repository;

import com.example.hearu.ai.response.domain.AiResponse;
import com.example.hearu.diary.domain.Diary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiResponseRepository extends JpaRepository<AiResponse, Long> {
    Optional<AiResponse> findByDiary(Diary diary);
}
