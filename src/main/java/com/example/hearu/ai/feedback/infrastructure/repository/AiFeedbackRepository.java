package com.example.hearu.ai.feedback.infrastructure.repository;

import com.example.hearu.ai.feedback.domain.AiFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiFeedbackRepository extends JpaRepository<AiFeedback, Long> {
    Optional<AiFeedback> findByAiResponse_AiResponseIdAndDeletedAtIsNull(Long aiResponseId);
}
