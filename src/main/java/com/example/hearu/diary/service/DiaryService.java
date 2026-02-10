package com.example.hearu.diary.service;

import com.example.hearu.diary.event.dto.DiaryAiResponseRequestedEvent;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final UserService userService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public DiaryCreateResponse createDiary(Long userId, DiaryCreateRequest request) {

        // 1. User 엔티티 조회
        User user = userService.getUserOrThrow(userId);

        // 2. User 닉네임이 존재하는지 판단(정책)
        user.validateUserNickNameExists();

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
}