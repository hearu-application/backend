package com.example.hearu.diary.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.hearu.ai.feedback.service.AiFeedbackService;
import com.example.hearu.ai.response.domain.AiResponseErrorCode;
import com.example.hearu.ai.response.service.AiResponseService;
import com.example.hearu.auth.domain.ProviderType;
import com.example.hearu.common.util.exception.BusinessException;
import com.example.hearu.diary.domain.Diary;
import com.example.hearu.diary.domain.EmotionType;
import com.example.hearu.diary.domain.error.DiaryErrorCode;
import com.example.hearu.diary.dto.request.DiaryCreateRequest;
import com.example.hearu.diary.dto.response.DiaryCreateResponse;
import com.example.hearu.diary.dto.response.DiaryDetailResponse;
import com.example.hearu.diary.dto.response.DiaryTodayCountResponse;
import com.example.hearu.diary.event.DiaryAiResponseRequestedEvent;
import com.example.hearu.diary.infrastructure.DiaryRepository;
import com.example.hearu.user.domain.ToneType;
import com.example.hearu.user.domain.User;
import com.example.hearu.user.domain.error.UserErrorCode;
import com.example.hearu.user.service.UserService;

@ExtendWith(MockitoExtension.class)
public class DiaryServiceTest {

    @Mock
    DiaryRepository diaryRepository;

    @Mock
    UserService userService;

    @Mock
    AiResponseService aiResponseService;

    @Mock
    AiFeedbackService aiFeedbackService;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    DiaryService diaryService;

    private User user;
    private Diary diary;

    @BeforeEach
    void setUp() {
        user = User.create(
            "example@naver.com",
            ProviderType.KAKAO,
            "1234567890"
        );
        ReflectionTestUtils.setField(user, "userId", 1L);

        diary = Diary.create(user, "내용", EmotionType.JOY, LocalDate.now());
        ReflectionTestUtils.setField(diary, "diaryId", 1L);
    }

    @Nested
    @DisplayName("일기 생성")
    class CreateDiary {

        private DiaryCreateRequest request;

        @BeforeEach
        void setUp() {
            // diaryDate 생략(null) → 오늘로 처리되는 기본 케이스
            request = new DiaryCreateRequest("내용", EmotionType.JOY, null);
        }

        @Test
        @DisplayName("사용자가 없는 경우, 예외 처리")
        void user_not_found() {
            given(userService.getUserOrThrow(1L)).willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

            assertThatThrownBy(() -> diaryService.createDiary(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.USER_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("닉네임이 없는 경우, 예외 처리")
        void nickname_not_exist() {
            given(userService.getUserOrThrow(1L)).willReturn(user);

            assertThatThrownBy(() -> diaryService.createDiary(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.NICKNAME_REQUIRED.getMessage());
        }

        @Test
        @DisplayName("하루 일기 제한에 걸리는 경우, 예외 처리")
        void limit_exceeded() {
            user.updateNickname("용준");
            LocalDate today = LocalDate.now();
            given(userService.getUserOrThrow(1L)).willReturn(user);
            given(diaryRepository.countAllByUser_UserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                1L, today.atStartOfDay(), today.plusDays(1).atStartOfDay()
            )).willReturn(10);

            assertThatThrownBy(() -> diaryService.createDiary(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DiaryErrorCode.DIARY_DAILY_LIMIT_EXCEEDED.getMessage());
        }

        @Test
        @DisplayName("성공")
        void success() {
            user.updateNickname("용준");
            // 기본값(HONORIFIC)과 다른 값을 넣어야 이벤트에 실린 값이 전달된 것인지 확인된다
            user.updateToneType(ToneType.INFORMAL);
            LocalDate today = LocalDate.now();
            given(userService.getUserOrThrow(1L)).willReturn(user);
            given(diaryRepository.countAllByUser_UserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                1L, today.atStartOfDay(), today.plusDays(1).atStartOfDay()
            )).willReturn(1);

            DiaryCreateResponse response = diaryService.createDiary(1L, request);

            // 1. 응답값 검증 (diaryDate 생략 → 오늘)
            assertThat(response.content()).isEqualTo(request.content());
            assertThat(response.emotionType()).isEqualTo(request.emotionType());
            assertThat(response.diaryDate()).isEqualTo(today);

            // 2. save() 호출 검증
            verify(diaryRepository).save(any(Diary.class));

            // 3. AI 응답(PENDING) 생성 호출 검증
            verify(aiResponseService).createPending(any(Diary.class));

            // 4. 이벤트 검증
            ArgumentCaptor<DiaryAiResponseRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(DiaryAiResponseRequestedEvent.class);
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

            DiaryAiResponseRequestedEvent event = eventCaptor.getValue();
            assertThat(event.content()).isEqualTo(request.content());
            assertThat(event.emotionType()).isEqualTo(request.emotionType());
            assertThat(event.nickname()).isEqualTo(user.getNickname());
            assertThat(event.toneType()).isEqualTo(ToneType.INFORMAL);
            assertThat(event.daysAgo()).isZero();
        }

        @Test
        @DisplayName("과거 날짜(범위 내)로 작성하면 diaryDate에 그 날짜가 반영된다")
        void backdated_within_range() {
            user.updateNickname("용준");
            LocalDate today = LocalDate.now();
            LocalDate targetDate = today.minusDays(3);
            given(userService.getUserOrThrow(1L)).willReturn(user);
            given(diaryRepository.countAllByUser_UserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                1L, today.atStartOfDay(), today.plusDays(1).atStartOfDay()
            )).willReturn(0);

            DiaryCreateResponse response =
                diaryService.createDiary(1L, new DiaryCreateRequest("내용", EmotionType.JOY, targetDate));

            assertThat(response.diaryDate()).isEqualTo(targetDate);

            ArgumentCaptor<Diary> diaryCaptor = ArgumentCaptor.forClass(Diary.class);
            verify(diaryRepository).save(diaryCaptor.capture());
            assertThat(diaryCaptor.getValue().getDiaryDate()).isEqualTo(targetDate);

            ArgumentCaptor<DiaryAiResponseRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(DiaryAiResponseRequestedEvent.class);
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().daysAgo()).isEqualTo(3);
        }

        @Test
        @DisplayName("작성 가능 범위(오늘-7)를 벗어난 과거 날짜인 경우, 예외 처리")
        void date_before_range() {
            user.updateNickname("용준");
            LocalDate outOfRange = LocalDate.now().minusDays(8);
            given(userService.getUserOrThrow(1L)).willReturn(user);

            assertThatThrownBy(() -> diaryService.createDiary(
                    1L, new DiaryCreateRequest("내용", EmotionType.JOY, outOfRange)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DiaryErrorCode.DIARY_DATE_OUT_OF_RANGE.getMessage());

            verify(diaryRepository, never()).save(any(Diary.class));
        }

        @Test
        @DisplayName("미래 날짜인 경우, 예외 처리")
        void date_in_future() {
            user.updateNickname("용준");
            LocalDate future = LocalDate.now().plusDays(1);
            given(userService.getUserOrThrow(1L)).willReturn(user);

            assertThatThrownBy(() -> diaryService.createDiary(
                    1L, new DiaryCreateRequest("내용", EmotionType.JOY, future)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DiaryErrorCode.DIARY_DATE_OUT_OF_RANGE.getMessage());

            verify(diaryRepository, never()).save(any(Diary.class));
        }

        @Test
        @DisplayName("오늘 이미 10개를 작성했으면 과거 날짜여도 제한에 걸린다")
        void limit_counts_today_even_for_backdated() {
            user.updateNickname("용준");
            LocalDate today = LocalDate.now();
            given(userService.getUserOrThrow(1L)).willReturn(user);
            given(diaryRepository.countAllByUser_UserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                1L, today.atStartOfDay(), today.plusDays(1).atStartOfDay()
            )).willReturn(10);

            assertThatThrownBy(() -> diaryService.createDiary(
                    1L, new DiaryCreateRequest("내용", EmotionType.JOY, today.minusDays(3))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DiaryErrorCode.DIARY_DAILY_LIMIT_EXCEEDED.getMessage());
        }
    }

    @Nested
    @DisplayName("일기 상세 조회")
    class GetDiaryDetail {

        @Test
        @DisplayName("일기가 없는 경우, 예외 처리")
        void diary_not_found() {
            given(diaryRepository.findByDiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> diaryService.getDiaryDetail(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DiaryErrorCode.DIARY_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("권한이 없는 경우, 예외 처리")
        void forbidden_diary() {
            given(diaryRepository.findByDiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(diary));

            assertThatThrownBy(() -> diaryService.getDiaryDetail(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DiaryErrorCode.DIARY_ACCESS_DENIED.getMessage());
        }

        @Test
        @DisplayName("성공")
        void success() {
            given(diaryRepository.findByDiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(diary));

            DiaryDetailResponse result = diaryService.getDiaryDetail(1L, 1L);

            assertThat(result.diaryId()).isEqualTo(diary.getDiaryId());
            assertThat(result.content()).isEqualTo(diary.getContent());
            assertThat(result.emotionType()).isEqualTo(diary.getEmotionType());
        }
    }

    @Nested
    @DisplayName("오늘 일기 작성 횟수 조회")
    class GetTodayDiaryCount {

        @Test
        @DisplayName("성공")
        void success() {
            LocalDate today = LocalDate.now();
            LocalDateTime start = today.atStartOfDay();
            LocalDateTime end = today.plusDays(1).atStartOfDay();
            given(diaryRepository
                .countAllByUser_UserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(1L, start, end))
                .willReturn(1);

            DiaryTodayCountResponse result = diaryService.getTodayDiaryCount(1L);

            assertThat(result.todayDiaryCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("일기 월별 캘린더 조회")
    class GetCalendarDiaries {

        private YearMonth yearMonth;

        @BeforeEach
        void setUp() {
            yearMonth = YearMonth.now();
        }

        @Test
        @DisplayName("일기가 없는 경우, 빈 목록 반환")
        void empty() {
            LocalDate start = yearMonth.atDay(1);
            LocalDate end = yearMonth.plusMonths(1).atDay(1);
            given(diaryRepository.findCalendarDiaries(1L, start, end)).willReturn(List.of());

            List<DiaryDetailResponse> result = diaryService.getCalendarDiaries(1L, yearMonth);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("성공")
        void success() {
            LocalDate start = yearMonth.atDay(1);
            LocalDate end = yearMonth.plusMonths(1).atDay(1);
            LocalDateTime createdAt = start.atStartOfDay();
            List<DiaryDetailResponse> diaries = List.of(
                new DiaryDetailResponse(1L, "내용1", EmotionType.ANGER, start, createdAt, createdAt),
                new DiaryDetailResponse(2L, "내용2", EmotionType.NEUTRAL, start, createdAt, createdAt)
            );
            given(diaryRepository.findCalendarDiaries(1L, start, end)).willReturn(diaries);

            List<DiaryDetailResponse> result = diaryService.getCalendarDiaries(1L, yearMonth);

            assertThat(result.getFirst().emotionType()).isEqualTo(diaries.getFirst().emotionType());
            assertThat(result.getFirst().content()).isEqualTo(diaries.getFirst().content());
            assertThat(result.get(1).emotionType()).isEqualTo(diaries.get(1).emotionType());
            assertThat(result.get(1).content()).isEqualTo(diaries.get(1).content());
        }
    }

    @Nested
    @DisplayName("일기 삭제")
    class DeleteDiary {

        @Test
        @DisplayName("일기가 없는 경우, 예외 처리")
        void diary_not_found() {
            given(diaryRepository.findByDiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> diaryService.deleteDiary(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DiaryErrorCode.DIARY_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("본인 일기가 아닌 경우, 예외 처리")
        void forbidden_diary() {
            given(diaryRepository.findByDiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(diary));

            assertThatThrownBy(() -> diaryService.deleteDiary(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DiaryErrorCode.DIARY_ACCESS_DENIED.getMessage());
        }

        @Test
        @DisplayName("성공")
        void success() {
            given(diaryRepository.findByDiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(diary));

            diaryService.deleteDiary(1L, 1L);

            assertThat(diary.getDeletedAt()).isNotNull();
            verify(aiResponseService).softDeleteByDiaryId(1L);
            verify(aiFeedbackService).softDeleteByDiaryId(1L);
        }
    }

    @Nested
    @DisplayName("AI 응답 요청")
    class RequestAiResponse {

        @Test
        @DisplayName("사용자가 없는 경우, 예외 처리")
        void user_not_found() {
            given(userService.getUserOrThrow(1L)).willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

            assertThatThrownBy(() -> diaryService.requestAiResponse(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.USER_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("일기가 없는 경우, 예외 처리")
        void diary_not_found() {
            given(userService.getUserOrThrow(1L)).willReturn(user);
            given(diaryRepository.findByDiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> diaryService.requestAiResponse(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DiaryErrorCode.DIARY_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("권한이 없는 경우, 예외 처리")
        void forbidden_diary() {
            User otherUser = User.create("other@naver.com", ProviderType.GOOGLE, "4444444444");
            given(userService.getUserOrThrow(2L)).willReturn(otherUser);
            given(diaryRepository.findByDiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(diary));

            assertThatThrownBy(() -> diaryService.requestAiResponse(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DiaryErrorCode.DIARY_ACCESS_DENIED.getMessage());
        }

        @Test
        @DisplayName("이미 완료된 AI 응답인 경우, 예외 처리 및 이벤트 미발행")
        void already_completed() {
            given(userService.getUserOrThrow(1L)).willReturn(user);
            given(diaryRepository.findByDiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(diary));
            willThrow(new BusinessException(AiResponseErrorCode.AI_RESPONSE_ALREADY_COMPLETED))
                .given(aiResponseService).markPending(1L);

            assertThatThrownBy(() -> diaryService.requestAiResponse(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(AiResponseErrorCode.AI_RESPONSE_ALREADY_COMPLETED.getMessage());

            verify(applicationEventPublisher, never()).publishEvent(any(DiaryAiResponseRequestedEvent.class));
        }

        @Test
        @DisplayName("성공")
        void success() {
            given(userService.getUserOrThrow(1L)).willReturn(user);
            given(diaryRepository.findByDiaryIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(diary));

            diaryService.requestAiResponse(1L, 1L);

            // 상태 초기화가 이벤트 발행보다 먼저여야 폴링이 재시도 직후 PENDING을 읽는다
            InOrder inOrder = Mockito.inOrder(aiResponseService, applicationEventPublisher);
            inOrder.verify(aiResponseService).markPending(1L);
            inOrder.verify(applicationEventPublisher).publishEvent(any(DiaryAiResponseRequestedEvent.class));

            ArgumentCaptor<DiaryAiResponseRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(DiaryAiResponseRequestedEvent.class);
            verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

            DiaryAiResponseRequestedEvent event = eventCaptor.getValue();
            assertThat(event.diaryId()).isEqualTo(diary.getDiaryId());
            assertThat(event.content()).isEqualTo(diary.getContent());
            assertThat(event.emotionType()).isEqualTo(diary.getEmotionType());
            assertThat(event.userId()).isEqualTo(user.getUserId());
            assertThat(event.nickname()).isEqualTo(user.getNickname());
            assertThat(event.toneType()).isEqualTo(user.getToneType());
            assertThat(event.daysAgo()).isZero();
        }
    }
}
