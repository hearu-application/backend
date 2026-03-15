package com.example.hearu.ai.character.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hearu.ai.character.domain.Companion;
import com.example.hearu.ai.character.domain.error.CompanionErrorCode;
import com.example.hearu.ai.character.dto.response.CompanionResponse;
import com.example.hearu.ai.character.infrastructure.CompanionRepository;
import com.example.hearu.common.util.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanionService {

    private final CompanionRepository companionRepository;

    public List<CompanionResponse> getAllCompanions() {

        // 1. 캐릭터 전체 목록 조회
        List<Companion> companions = companionRepository.findAll();

        // 2. DTO 리스트로 반환
        return companions.stream().map(CompanionResponse::fromEntity).toList();
    }

    public Companion getDefaultOrThrow() {
        return companionRepository.findByIsDefaultTrue()
            .orElseThrow(() -> {
                log.warn("기본 캐릭터가 존재하지 않습니다.");
                return new BusinessException(CompanionErrorCode.COMPANION_NOT_FOUND);
            });
    }

    public Companion getOrThrow(Long companionId) {
        return companionRepository.findById(companionId)
            .orElseThrow(() -> {
                log.warn("캐릭터가 존재하지 않습니다. companionId={}", companionId);
                return new BusinessException(CompanionErrorCode.COMPANION_NOT_FOUND);
            });
    }
}
