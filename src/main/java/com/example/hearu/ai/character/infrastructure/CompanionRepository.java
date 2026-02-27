package com.example.hearu.ai.character.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hearu.ai.character.domain.Companion;

public interface CompanionRepository extends JpaRepository<Companion, Long> {
}
