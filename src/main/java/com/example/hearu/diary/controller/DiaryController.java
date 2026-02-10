package com.example.hearu.diary.controller;

import com.example.hearu.common.response.ApiResponse;
import com.example.hearu.diary.dto.request.DiaryCreateRequest;
import com.example.hearu.diary.dto.response.DiaryCreateResponse;
import com.example.hearu.diary.dto.response.DiaryDetailResponse;
import com.example.hearu.diary.service.DiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @PostMapping
    public ResponseEntity<ApiResponse<DiaryCreateResponse>> createDiary(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid DiaryCreateRequest request
    ) {

        DiaryCreateResponse response = diaryService.createDiary(userId, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "일기 작성이 완료되었습니다.",
                        response,
                        HttpStatus.CREATED
                )
        );
    }

    @GetMapping("/{diaryId}")
    public ResponseEntity<ApiResponse<DiaryDetailResponse>> getDiaryDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long diaryId
    ) {

        DiaryDetailResponse response = diaryService.getDiaryDetail(userId, diaryId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "일기 상세 조회가 완료되었습니다.",
                        response
                )
        );
    }

    @DeleteMapping("/{diaryId}")
    public ResponseEntity<ApiResponse<Void>> deleteDiary(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long diaryId
    ) {

        diaryService.deleteDiary(userId, diaryId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "일기 삭제가 완료되었습니다.",
                        null
                )
        );
    }
}