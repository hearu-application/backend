package com.example.hearu.ai.response.domain;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.example.hearu.diary.domain.EmotionType;
import com.example.hearu.user.domain.ToneType;

@DisplayName("PromptBuilder")
public class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Nested
    @DisplayName("시스템 프롬프트 말투 분기")
    class SystemPromptBuild {

        @Test
        @DisplayName("반말은 모음 늘이기 규칙을 포함하고 존댓말 규칙은 포함하지 않는다")
        void informal_tone() {
            String prompt = promptBuilder.systemPromptBuild("용준", ToneType.INFORMAL);

            assertThat(prompt).contains("구어체 모음 연장을 적용한다");
            assertThat(prompt).doesNotContain("친근한 존댓말(해요체)로 통일한다");
            assertThat(prompt).doesNotContain("존댓말");
        }

        @Test
        @DisplayName("존댓말은 해요체 규칙을 포함하고 반말 모음 늘이기 규칙은 포함하지 않는다")
        void honorific_tone() {
            String prompt = promptBuilder.systemPromptBuild("용준", ToneType.HONORIFIC);

            assertThat(prompt).contains("친근한 존댓말(해요체)로 통일한다");
            assertThat(prompt).contains("항상 '님'을 붙인다");
            assertThat(prompt).doesNotContain("구어체 모음 연장을 적용한다");
        }

        @Test
        @DisplayName("캐릭터·애교 소리·민감 주제 안내·출력 형식은 두 말투에 공통으로 들어간다")
        void common_blocks_in_both_tones() {
            for (ToneType toneType : ToneType.values()) {
                String prompt = promptBuilder.systemPromptBuild("용준", toneType);

                assertThat(prompt).contains("너는 \"봉봉이\"라는 이름의 리트리버 강아지야");
                // 분석형 AI 상담사 톤을 막는 캐릭터 정의
                assertThat(prompt).contains("'AI 심리 상담사'처럼 행동하지 않는다");
                assertThat(prompt).contains("'헤헤', '히히', '우와', '킁킁' 소리만 낸다");
                // 위기 대응은 특정 기관·전화번호 없이 일반 표현으로만 안내한다
                assertThat(prompt).contains("'전문 상담 기관'/'상담 센터' 같은 일반적인 표현으로만 안내");
                assertThat(prompt).contains("응답 내용은 반드시 500자 이내로 작성한다");
                // 조각 조립에서 .replace를 빠뜨리면 {{character}} 같은 구조 토큰이 프롬프트에 그대로 샌다.
                // 정상 출력엔 {{가 없고({"response"}는 홑 {), 이 한 줄이 여섯 토큰 누락을 모두 잡는다.
                assertThat(prompt).doesNotContain("{{");
            }
        }

        @Test
        @DisplayName("few-shot 예시는 두 말투 모두에서 제거되어 [예시] 블록이 없다")
        void no_few_shot_examples_in_both_tones() {
            for (ToneType toneType : ToneType.values()) {
                String prompt = promptBuilder.systemPromptBuild("용준", toneType);

                assertThat(prompt).doesNotContain("[예시]");
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

        @Test
        @DisplayName("받침 있는 이름은 반말 호칭 규칙에 호격 '아'·주격 '이가'로 주입된다")
        void informal_injects_name_with_batchim_particles() {
            String prompt = promptBuilder.systemPromptBuild("용준", ToneType.INFORMAL);

            assertThat(prompt).contains("부를 때는 '용준아', 주어로 쓸 때는 '용준이가'처럼 쓴다");
            // 치환 토큰이 남으면 프롬프트에 그대로 노출된다
            assertThat(prompt).doesNotContain("{name");
        }

        @Test
        @DisplayName("받침 없는 이름은 반말 호칭 규칙에 호격 '야'·주격 '가'로 주입된다")
        void informal_injects_name_without_batchim_particles() {
            String prompt = promptBuilder.systemPromptBuild("수지", ToneType.INFORMAL);

            assertThat(prompt).contains("부를 때는 '수지야', 주어로 쓸 때는 '수지가'처럼 쓴다");
            // 받침 없는 이름에 '이가'·'아'가 붙으면 비문이 된다
            assertThat(prompt).doesNotContain("수지이가");
            assertThat(prompt).doesNotContain("'수지아'");
            assertThat(prompt).doesNotContain("{name");
        }

        @Test
        @DisplayName("존댓말은 이름만 주입되고 조사 토큰이 남지 않는다")
        void honorific_injects_plain_name() {
            String prompt = promptBuilder.systemPromptBuild("수지", ToneType.HONORIFIC);

            assertThat(prompt).contains("사용자 이름: 수지");
            assertThat(prompt).doesNotContain("{name");
        }
    }

    @Nested
    @DisplayName("유저 프롬프트")
    class UserPromptBuild {

        @Test
        @DisplayName("일기 원문과 감정 표시명을 그대로 담는다")
        void includes_content_and_emotion() {
            String prompt = promptBuilder.userPromptBuild("오늘은 좋은 하루였다", EmotionType.JOY, 0);

            assertThat(prompt).contains("입력: 오늘은 좋은 하루였다");
            assertThat(prompt).contains("감정: " + EmotionType.JOY.getDisplayName());
        }

        @Test
        @DisplayName("오늘 일기는 날짜 항목을 넣지 않는다")
        void today_has_no_date() {
            String prompt = promptBuilder.userPromptBuild("오늘은 좋은 하루였다", EmotionType.JOY, 0);

            assertThat(prompt).doesNotContain("날짜:");
        }

        @ParameterizedTest(name = "{0}일 전 → {1}")
        @CsvSource({"1, 어제", "2, 그저께", "3, 며칠 전", "30, 며칠 전"})
        @DisplayName("과거 날짜 일기는 날짜 차이를 상대 표현으로 담는다")
        void past_diary_has_relative_date(long daysAgo, String expected) {
            String prompt = promptBuilder.userPromptBuild("오늘은 좋은 하루였다", EmotionType.JOY, daysAgo);

            assertThat(prompt).contains("날짜: " + expected);
        }
    }
}
