package com.example.hearu.ai.response.domain;

import com.example.hearu.common.entity.BaseEntity;
import com.example.hearu.diary.domain.Diary;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "ai_response")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiResponse extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_response_id")
    private Long aiResponseId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diary_id", nullable = false)
    private Diary diary;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_response_status_type", nullable = false)
    private AiResponseStatusType aiResponseStatusType;

    private AiResponse(Diary diary) {
        this.diary = diary;
        this.aiResponseStatusType = AiResponseStatusType.PENDING;
    }

    public static AiResponse create(Diary diary) {
        return new AiResponse(diary);
    }

    public void completeResponse(String content) {
        this.aiResponseStatusType = AiResponseStatusType.COMPLETED;
        this.content = content;
    }

    public void failResponse() {
        if (this.aiResponseStatusType == AiResponseStatusType.COMPLETED) {
            return;
        }
        this.aiResponseStatusType = AiResponseStatusType.FAILED;
    }
}
