package com.example.hearu.diary.domain;

import com.example.hearu.common.util.exception.BusinessException;
import com.example.hearu.common.encrypt.ContentCryptoConverter;
import com.example.hearu.common.entity.BaseEntity;
import com.example.hearu.diary.domain.error.DiaryErrorCode;
import com.example.hearu.user.domain.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "diary")
public class Diary extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "diary_id")
    private Long diaryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // AiResponse와는 단방향(AiResponse -> Diary)으로만 연결한다. Diary가 AiResponse를 역참조하면
    // @OneToOne 기본 EAGER 때문에 모든 Diary 조회에 ai_response SELECT가 딸려와 N+1을 유발한다.
    // AiResponse의 생성/삭제는 AiResponseService가 담당한다.

    @Convert(converter = ContentCryptoConverter.class)
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "emotion_type", nullable = false, length = 50)
    private EmotionType emotionType;

    private Diary(User user, String content, EmotionType emotionType) {
        this.user = user;
        this.content = content;
        this.emotionType = emotionType;
    }

    public static Diary create(User user, String content, EmotionType emotionType) {
        return new Diary(user, content, emotionType);
    }

    public void validateOwner(Long userId) {
        if (!this.user.getUserId().equals(userId)) {
            // 인가 실패는 항상 남긴다. BusinessException 처리는 DEBUG라 여기서 안 남기면 prod에 무기록이다.
            log.warn("일기 접근 권한이 없습니다. 요청 userId={}, 소유자 userId={}, diaryId={}",
                userId, this.user.getUserId(), this.diaryId);
            throw new BusinessException(DiaryErrorCode.DIARY_ACCESS_DENIED);
        }
    }
}