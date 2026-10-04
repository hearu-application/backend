package com.example.hearu.diary.event;

import com.example.hearu.diary.domain.Diary;
import com.example.hearu.diary.domain.EmotionType;
import com.example.hearu.user.domain.ToneType;
import com.example.hearu.user.domain.User;

public record DiaryAiResponseRequestedEvent(
    Long diaryId,
    String content,
    EmotionType emotionType,
    Long userId,
    String nickname,
    ToneType toneType
) {

    // 이벤트는 반드시 여기서 만든다. 최초 생성·수동 재요청·회수 재발행이 같은 값을 싣도록 조립을 한 곳에 둔다
    // (필드를 추가했는데 한 경로만 빠뜨리면 재시도할 때만 프롬프트가 달라지고, 컴파일·테스트로 안 걸린다).
    public static DiaryAiResponseRequestedEvent from(Diary diary, User user) {
        return new DiaryAiResponseRequestedEvent(
            diary.getDiaryId(),
            diary.getContent(),
            diary.getEmotionType(),
            user.getUserId(),
            user.getNickname(),
            user.getToneType()
        );
    }
}
