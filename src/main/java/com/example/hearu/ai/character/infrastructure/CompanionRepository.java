package com.example.hearu.ai.character.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hearu.ai.character.domain.Companion;

public interface CompanionRepository extends JpaRepository<Companion, Long> {
    Optional<Companion> findByIsDefault(Boolean isDefault);
}
