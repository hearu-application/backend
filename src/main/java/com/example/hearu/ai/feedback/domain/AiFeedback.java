package com.example.hearu.ai.feedback.domain;

import com.example.hearu.ai.response.domain.AiResponse;
import com.example.hearu.common.entity.BaseEntity;
import com.example.hearu.common.util.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "ai_feedback")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiFeedback extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_feedback_id")
    private Long aiFeedbackId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_response_id", nullable = false)
    private AiResponse aiResponse;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false)
    private FeedbackType feedbackType;

    @Enumerated(EnumType.STRING)
    @Column(name = "dislike_reason_type")
    private DislikeReasonType dislikeReasonType;

    @Column(name = "custom_text", length = 50)
    private String customText;

    private AiFeedback(AiResponse aiResponse, FeedbackType feedbackType,
                       DislikeReasonType dislikeReasonType, String customText) {
        this.aiResponse = aiResponse;
        applyFeedback(feedbackType, dislikeReasonType, customText);
    }

    public static AiFeedback create(AiResponse aiResponse, FeedbackType feedbackType,
                                    DislikeReasonType dislikeReasonType, String customText) {
        return new AiFeedback(aiResponse, feedbackType, dislikeReasonType, customText);
    }

    public void update(FeedbackType feedbackType, DislikeReasonType dislikeReasonType, String customText) {
        applyFeedback(feedbackType, dislikeReasonType, customText);
    }

    private void applyFeedback(FeedbackType feedbackType, DislikeReasonType dislikeReasonType, String customText) {
        if (feedbackType == FeedbackType.LIKE) {
            this.feedbackType = feedbackType;
            this.dislikeReasonType = null;
            this.customText = null;
            return;
        }

        if (dislikeReasonType == DislikeReasonType.ETC && (customText == null || customText.isBlank())) {
            throw new BusinessException(AiFeedbackErrorCode.REASON_TEXT_REQUIRED);
        }

        this.feedbackType = feedbackType;
        this.dislikeReasonType = dislikeReasonType;
        this.customText = (dislikeReasonType == DislikeReasonType.ETC) ? customText : null;
    }
}
