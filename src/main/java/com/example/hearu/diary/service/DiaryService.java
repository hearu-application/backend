package com.example.hearu.diary.service;

import com.example.hearu.common.util.exception.BusinessException;
import com.example.hearu.diary.domain.error.DiaryErrorCode;
import com.example.hearu.diary.dto.response.DiaryDetailResponse;
import com.example.hearu.diary.dto.response.DiaryTodayCountResponse;
import com.example.hearu.diary.event.DiaryAiResponseRequestedEvent;
import com.example.hearu.user.service.UserService;
import com.example.hearu.diary.dto.request.DiaryCreateRequest;
import com.example.hearu.diary.dto.response.DiaryCreateResponse;
import com.example.hearu.diary.domain.Diary;
import com.example.hearu.user.domain.User;
import com.example.hearu.diary.infrastructure.DiaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final UserService userService;
    private final ApplicationEventPublisher applicationEventPublisher;

    private static final int DIARY_DAILY_LIMIT = 10;

    @Transactional
    public DiaryCreateResponse createDiary(Long userId, DiaryCreateRequest request) {

        // 1. User 엔티티 조회
        User user = userService.getUserOrThrow(userId);

        // 2. User 닉네임이 존재하는지 판단(정책)
        user.validateUserNickNameExists();

        // 3. 하루 일기 제한 검사(정책)
        LocalDate today = LocalDate.now();
        long count = diaryRepository.countAllByUser_UserIdAndCreatedAtBetween(
            userId,
            today.atStartOfDay(),
            today.atTime(LocalTime.MAX)
        );
        if (count >= DIARY_DAILY_LIMIT) {
            throw new BusinessException(DiaryErrorCode.DIARY_DAILY_LIMIT_EXCEEDED);
        }

        // 3. Diary 엔티티 생성 및 저장
        Diary diary = Diary.create(
                user,
                request.content(),
                request.emotionType()
        );
        diaryRepository.save(diary);

        // 4. Ai 응답 이벤트 발행
        applicationEventPublisher.publishEvent(
            new DiaryAiResponseRequestedEvent(
                diary.getDiaryId(),
                diary.getContent(),
                diary.getEmotionType(),
                user.getUserId(),
                user.getNickName()
            )
        );

        // 5. 생성된 일기 정보 반환
        return new DiaryCreateResponse(
                diary.getDiaryId(),
                diary.getContent(),
                diary.getEmotionType()
        );
    }

    @Transactional(readOnly = true)
    public DiaryDetailResponse getDiaryDetail(Long userId, Long diaryId) {
        // 1. Diary 조회
        Diary diary = getDiaryOrThrow(userId, diaryId);

        // 2. 본인 일기 검증
        diary.validateOwner(userId);

        // 3. DTO 반환
        return new DiaryDetailResponse(
                diary.getDiaryId(),
                diary.getContent(),
                diary.getEmotionType(),
                diary.getCreatedAt(),
                diary.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public DiaryTodayCountResponse getTodayDiaryCount(Long userId) {

        // 1. 오늘 날짜 확인
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);

        // 2. 오늘 일기 작성 횟수 조회
        int todayDiaryCount = diaryRepository.countAllByUser_UserIdAndCreatedAtBetween(userId, start, end);

        return new DiaryTodayCountResponse(todayDiaryCount);
    }

    @Transactional(readOnly = true)
    public List<DiaryDetailResponse> getCalendarDiaries(Long userId, YearMonth yearMonth) {

        // 1. 해당 월의 시작일과 종료일 계산
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.plusMonths(1).atDay(1).atStartOfDay();


        // 2. User 조회
        User user = userService.getUserOrThrow(userId);

        // 3. DB에서 해당 월의 일기 목록 조회
        List<Diary> diaries = diaryRepository.findByUserAndDeletedAtIsNullAndCreatedAtBetweenOrderByCreatedAtDesc(
                user,
                start,
                end
        );

        // 4. DTO로 변환
        return diaries.stream().map(DiaryDetailResponse::fromEntity).toList();
    }

    @Transactional
    public void deleteDiary(Long userId, Long diaryId) {
        // 1. User 엔티티 조회
        User user = userService.getUserOrThrow(userId);

        // 2. 일기 조회
        Diary diary = getDiaryOrThrow(userId, diaryId);

        // 3. 본인 일기 검증
        diary.validateOwner(user.getUserId());

        // 4. 일기 삭제
        diary.softDelete();
        diary.getAiResponse().softDelete();
    }


    @Transactional(readOnly = true)
    public Diary getDiaryOrThrow(Long userId, Long diaryId) {
        return diaryRepository.findByDiaryIdAndDeletedAtIsNull(diaryId)
                .orElseThrow(() -> {
                    log.warn("일기가 존재하지 않습니다. userId={}, diaryId={}", userId, diaryId);
                    return new BusinessException(DiaryErrorCode.DIARY_NOT_FOUND);
                });
    }

    @Transactional(readOnly = true)
    public void requestAiResponse(Long userId, Long diaryId) {
        // 1. User 엔티티 조회
        User user = userService.getUserOrThrow(userId);

        // 2. Diary 엔티티 조회
        Diary diary = getDiaryOrThrow(userId, diaryId);

        // 3. 일기 사용자 검증
        diary.validateOwner(userId);

        // 4. Ai 응답 이벤트 발행
        applicationEventPublisher.publishEvent(
                new DiaryAiResponseRequestedEvent(
                        diary.getDiaryId(),
                        diary.getContent(),
                        diary.getEmotionType(),
                        user.getUserId(),
                        user.getNickName()
                )
        );
    }
}