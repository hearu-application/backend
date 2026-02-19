package com.example.hearu.diary.controller;

import com.example.hearu.common.response.ApiResponse;
import com.example.hearu.diary.dto.request.DiaryCreateRequest;
import com.example.hearu.diary.dto.response.DiaryCalendarResponse;
import com.example.hearu.diary.dto.response.DiaryCreateResponse;
import com.example.hearu.diary.dto.response.DiaryDetailResponse;
import com.example.hearu.diary.service.DiaryService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@Tag(name = "Diary", description = "Diary API")
@RestController
@RequestMapping("/api/v1/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @SecurityRequirement(name = "Authorization")
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

    @SecurityRequirement(name = "Authorization")
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

    @SecurityRequirement(name = "Authorization")
    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<DiaryCalendarResponse>> getCalendarDiaries(
            @AuthenticationPrincipal Long userId,
            @RequestParam("yearMonth") YearMonth yearMonth
    ) {

        DiaryCalendarResponse response = diaryService.getCalendarDiaries(userId, yearMonth);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "캘린더 일기 목록 조회가 완료되었습니다.",
                        response
                )
        );
    }

    @SecurityRequirement(name = "Authorization")
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

    @SecurityRequirement(name = "Authorization")
    @PostMapping("/{diaryId}/ai-response")
    public ResponseEntity<ApiResponse<Void>> requestAiResponse(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long diaryId
    ) {
        diaryService.requestAiResponse(userId, diaryId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "AI 응답 요청 접수 완료",
                        null,
                        HttpStatus.ACCEPTED
                )
        );
    }
}