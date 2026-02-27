package com.example.hearu.ai.character.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.hearu.ai.character.domain.Companion;
import com.example.hearu.ai.character.dto.response.CompanionResponse;
import com.example.hearu.ai.character.infrastructure.CompanionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompanionService {

    private final CompanionRepository companionRepository;

    public List<CompanionResponse> getAllCompanions() {

        // 1. 캐릭터 전체 목록 조회
        List<Companion> companions = companionRepository.findAll();

        // 2. DTO 리스트로 반환
        return companions.stream().map(CompanionResponse::fromEntity).toList();
    }
}
