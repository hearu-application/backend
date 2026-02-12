package com.example.hearu.auth.infrastructure.repository;

import java.time.LocalDateTime;

import com.example.hearu.auth.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthRepository extends JpaRepository<RefreshToken, Long> {
    @Modifying
    int deleteByExpiresAtBefore(LocalDateTime now);
}