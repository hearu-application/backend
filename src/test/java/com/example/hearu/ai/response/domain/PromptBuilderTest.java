package com.example.hearu.ai.response.domain;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.example.hearu.diary.domain.EmotionType;
import com.example.hearu.user.domain.ToneType;

@DisplayName("PromptBuilder")
public class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Nested
    @DisplayName("시스템 프롬프트 말투 분기")
    class SystemPromptBuild {

        @Test
        @DisplayName("반말은 모음 늘이기 규칙과 반말 예시를 포함한다")
        void informal_tone() {
            String prompt = promptBuilder.systemPromptBuild("용준", ToneType.INFORMAL);

            assertThat(prompt).contains("문장 끝 모음 늘이기");
            assertThat(prompt).contains("속상했겠다아");
            assertThat(prompt).doesNotContain("존댓말");
            assertThat(prompt).doesNotContain("'님'을 붙이고");
        }

        @Test
        @DisplayName("존댓말은 모음 늘이기 규칙과 반말 예시를 포함하지 않는다")
        void honorific_tone() {
            String prompt = promptBuilder.systemPromptBuild("용준", ToneType.HONORIFIC);

            assertThat(prompt).contains("모든 문장을 존댓말로 끝낸다");
            assertThat(prompt).contains("'님'을 붙이고");
            assertThat(prompt).contains("속상하셨겠어요");
            assertThat(prompt).doesNotContain("문장 끝 모음 늘이기");
            // [예시]가 규칙보다 톤을 강하게 지배하므로, 반말 예시가 섞이면 존댓말 지시가 무력화된다
            assertThat(prompt).doesNotContain("속상했겠다아");
        }

        @Test
        @DisplayName("의성어·느낌표·대응 방침·출력 형식은 두 말투에 공통으로 들어간다")
        void common_blocks_in_both_tones() {
            for (ToneType toneType : ToneType.values()) {
                String prompt = promptBuilder.systemPromptBuild("용준", toneType);

                assertThat(prompt).contains("필수 의성어: 뚱땅뚱땅, 몽글몽글, 킁킁");
                // 의성어 하한만 있으면 무거운 일기에도 2개가 박혀 감정과 부딪힌다
                assertThat(prompt).contains("무거운 맥락에서는 1개 이하로 줄인다");
                assertThat(prompt).contains("민감한 상황에서는 느낌표를 사용하지 않는다");
                assertThat(prompt).contains("전문 기관에 도움 요청으로 유도해");
                assertThat(prompt).contains("400자 이내");
            }
        }

        @Test
        @DisplayName("공감 지침은 두 말투 모두에서 구체적 감정을 추측형으로 짚게 한다")
        void empathy_policy_in_both_tones() {
            for (ToneType toneType : ToneType.values()) {
                String prompt = promptBuilder.systemPromptBuild("용준", toneType);

                // 어떤 일기에도 붙는 템플릿 공감 방지
                assertThat(prompt).contains("구체적인 감정을 추측해 짚는다");
                // 추측을 단정으로 내놓으면 빗나갔을 때 더 상처가 된다
                assertThat(prompt).contains("단정하지 말고 추측형으로 여지를 남긴다");
                // 따라하기식 공감 방지
                assertThat(prompt).contains("그대로 되풀이하지 않는다");
            }
        }

        @Test
        @DisplayName("마무리 지침은 두 말투 모두에 들어가고, 제안을 붙이지 않는 갈래도 함께 명시된다")
        void closing_policy_in_both_tones() {
            for (ToneType toneType : ToneType.values()) {
                String prompt = promptBuilder.systemPromptBuild("용준", toneType);

                // 매 응답이 질문·제안으로 끝나는 것을 막는 상위 지침
                assertThat(prompt).contains("매 응답을 질문이나 제안으로 끝내지 않는다");
                // 가라앉은 갈래는 제안이 아니라 곁에 있어주는 마무리가 기본
                assertThat(prompt).contains("'슬픔'·'분노'");
                assertThat(prompt).contains("제안·질문 없이 곁에 있어주며 끝낸다");
                // 행동 제안이 허용되는 좁은 조건이 함께 있어야 무거운 실패에 제안이 붙지 않는다
                assertThat(prompt).contains("가볍게 축 처지는 날에만");
                assertThat(prompt).contains("일상 제안을 덧붙이지 않는다");
                // 감정 태그는 사용자가 고른 값이라 본문과 어긋날 수 있다
                assertThat(prompt).contains("감정 분기는 태그보다 본문을 우선한다");
            }
        }

        @Test
        @DisplayName("닉네임은 두 말투 모두에 주입된다")
        void nickname_injected_in_both_tones() {
            for (ToneType toneType : ToneType.values()) {
                assertThat(promptBuilder.systemPromptBuild("용준", toneType))
                    .contains("사용자 이름: 용준");
            }
        }
    }

    @Nested
    @DisplayName("감정 커버리지")
    class EmotionCoverage {

        // [응답 구성]이 감정 이름을 문자열로 열거하므로, EmotionType에 값이 추가되면
        // 어느 갈래에도 걸리지 않는 감정이 조용히 생긴다. 컴파일도 다른 테스트도 잡지 못하는
        // 실패라서 여기서 막는다. 새 감정을 추가했다면 PromptBuilder의 [응답 구성] 3도 함께 고친다.
        @ParameterizedTest
        @EnumSource(EmotionType.class)
        @DisplayName("모든 감정이 [응답 구성]에 마무리 방식과 함께 명시된다")
        void every_emotion_has_a_closing_rule(EmotionType emotionType) {
            String quotedName = "'" + emotionType.getDisplayName() + "'";

            for (ToneType toneType : ToneType.values()) {
                assertThat(promptBuilder.systemPromptBuild("용준", toneType))
                    .as("%s(%s)에 대한 마무리 규칙이 [응답 구성]에 없습니다", emotionType, toneType)
                    .contains(quotedName);
            }
        }
    }
}
