package com.example.hearu.ai.character.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.hearu.ai.character.dto.response.CompanionResponse;
import com.example.hearu.ai.character.service.CompanionService;
import com.example.hearu.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Companion", description = "캐릭터 API")
@RestController
@RequestMapping("/api/v1/companions")
@RequiredArgsConstructor
public class CompanionController {

    private final CompanionService companionService;

    @SecurityRequirement(name = "Authorization")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CompanionResponse>>> getAllCompanions() {
        List<CompanionResponse> allCompanions = companionService.getAllCompanions();
        return ResponseEntity.ok(
                ApiResponse.success(
                        "캐릭터 전체 목록 조회가 완료되었습니다.",
                        allCompanions
                )
        );
    }
}
