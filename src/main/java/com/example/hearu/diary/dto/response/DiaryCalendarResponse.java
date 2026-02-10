package com.example.hearu.diary.dto.response;

import com.example.hearu.diary.domain.EmotionType;
import com.example.hearu.diary.domain.Diary;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class DiaryCalendarResponse {

    private final List<DiaryCalendarItem> diaries; // 해당 월의 일기 목록

    private DiaryCalendarResponse(List<DiaryCalendarItem> diaries) {
        this.diaries = diaries;
    }

    public static DiaryCalendarResponse from(List<Diary> diaries) {
        List<DiaryCalendarItem> items = diaries.stream()
                .map(DiaryCalendarItem::fromEntity)
                .toList();
        return new DiaryCalendarResponse(items);
    }

    public record DiaryCalendarItem(
            Long diaryId,
            LocalDate date,
            EmotionType emotionType
    ) {
        public static DiaryCalendarItem fromEntity(Diary diary) {
            return new DiaryCalendarItem(
                    diary.getDiaryId(),
                    diary.getCreatedAt().toLocalDate(),
                    diary.getEmotionType()
            );
        }
    }
}

