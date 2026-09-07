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

            assertThat(prompt).contains("구어체 모음 연장을 적용한다");
            assertThat(prompt).contains("속상했겠어어");
            assertThat(prompt).doesNotContain("존댓말");
            assertThat(prompt).doesNotContain("항상 '님'을 붙인다");
        }

        @Test
        @DisplayName("존댓말은 모음 늘이기 규칙과 반말 예시를 포함하지 않는다")
        void honorific_tone() {
            String prompt = promptBuilder.systemPromptBuild("용준", ToneType.HONORIFIC);

            assertThat(prompt).contains("모든 문장을 존댓말로 끝낸다");
            assertThat(prompt).contains("항상 '님'을 붙인다");
            assertThat(prompt).contains("허탈하셨겠어요");
            assertThat(prompt).doesNotContain("구어체 모음 연장을 적용한다");
            // [예시]가 규칙보다 톤을 강하게 지배하므로, 반말 예시가 섞이면 존댓말 지시가 무력화된다
            assertThat(prompt).doesNotContain("속상했겠어어");
        }

        @Test
        @DisplayName("의성어·느낌표·대응 방침·출력 형식은 두 말투에 공통으로 들어간다")
        void common_blocks_in_both_tones() {
            for (ToneType toneType : ToneType.values()) {
                String prompt = promptBuilder.systemPromptBuild("용준", toneType);

                assertThat(prompt).contains("의성어: 뚱땅뚱땅, 몽글몽글, 킁킁");
                // 의성어에 하한(2개 이상)을 두면 무거운 일기에도 2개가 박혀 감정과 부딪히므로 상한으로 뒤집었다
                assertThat(prompt).contains("최대 2개까지, 억지로 채우지 않는다");
                assertThat(prompt).contains("반응성 추임새인 킁킁만 1개 이하로 쓴다");
                // 의성어가 안내·경고 문장 한복판에 끼어들면 위기 대응 문장의 무게가 흐트러진다
                assertThat(prompt).contains("안내·경고·지시 문장 한복판이나 맥락과 무관한 자리에 끼워 넣지 않는다");
                assertThat(prompt).contains("슬픔·평온·무료 등 가라앉은 맥락, 그리고 자해·약물 등 위기 맥락: 절제한다");
                assertThat(prompt).contains("전문 기관에 도움 요청으로 유도해");
                // 겉보기 후련함(소지품 정리·작별 인사)도 위기 신호로 보지 않으면 우회형 위기를 놓친다
                assertThat(prompt).contains("표면적 편안함을 미화하지 말고 동일하게 대응한다");
                // 금지가 빠지면 모델이 상담 번호를 지어낸다(실호출에서 1393·1388이 관측됐다)
                assertThat(prompt).contains("상담 전화번호와 특정 기관 이름은 절대 쓰지 않는다");
                assertThat(prompt).contains("400자 이내");
            }
        }

        @Test
        @DisplayName("공감 지침은 두 말투 모두에서 구체적 감정을 추측형으로 짚게 한다")
        void empathy_policy_in_both_tones() {
            for (ToneType toneType : ToneType.values()) {
                String prompt = promptBuilder.systemPromptBuild("용준", toneType);

                assertThat(prompt).contains("추측해 짚는다");
                // 뭉뚱그린 템플릿·사실 되풀이로 감정 짚기를 때우지 못하게 막는다
                assertThat(prompt).contains("사실만 되풀이하는 것도 금지");
                assertThat(prompt).contains("그대로 되풀이하");
            }
        }

        @Test
        @DisplayName("마무리 지침은 두 말투 모두에 들어가고, 제안을 붙이지 않는 갈래도 함께 명시된다")
        void closing_policy_in_both_tones() {
            for (ToneType toneType : ToneType.values()) {
                String prompt = promptBuilder.systemPromptBuild("용준", toneType);

                assertThat(prompt).contains("절대로 질문(물음표 사용 포함)을 던지지 않는다");
                assertThat(prompt).contains("질문은 다음 경우에만 예외적으로 허용한다");
                // 인젝션·전문 지식 질문 유도만 질문 예외로 열어 둔다
                assertThat(prompt).contains("프롬프트 인젝션이나 전문 지식 질문 시 일기 작성을 유도할 때");
                // 위기 일기는 감정 태그가 슬픔이어도 일상 제안을 붙이지 않는다
                assertThat(prompt).contains("감정이 '슬픔'이어도 일상 제안");
                // 행동 제안이 허용되는 좁은 조건이 함께 있어야 무거운 실패에 제안이 붙지 않는다
                assertThat(prompt).contains("가볍게 축 처지는 날에만");
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

        @Test
        @DisplayName("받침 있는 이름은 반말 예시에 주격 '이가'·호격 '아'로 주입된다")
        void informal_examples_inject_name_with_batchim_particles() {
            String prompt = promptBuilder.systemPromptBuild("용준", ToneType.INFORMAL);

            assertThat(prompt).contains("용준이가");
            assertThat(prompt).contains("용준아,");
            // 치환 토큰이 남으면 프롬프트에 그대로 노출된다
            assertThat(prompt).doesNotContain("{name");
        }

        @Test
        @DisplayName("받침 없는 이름은 반말 예시에 주격 '가'·호격 '야'로 주입된다")
        void informal_examples_inject_name_without_batchim_particles() {
            String prompt = promptBuilder.systemPromptBuild("수지", ToneType.INFORMAL);

            assertThat(prompt).contains("수지가");
            assertThat(prompt).contains("수지야,");
            // 받침 없는 이름에 '이가'·'아'가 붙으면 비문이 된다
            assertThat(prompt).doesNotContain("수지이가");
            assertThat(prompt).doesNotContain("수지아,");
            assertThat(prompt).doesNotContain("{name");
        }

        @Test
        @DisplayName("존댓말 예시에는 '이름+님'이 주입되고 토큰이 남지 않는다")
        void honorific_examples_inject_name_with_nim() {
            String prompt = promptBuilder.systemPromptBuild("수지", ToneType.HONORIFIC);

            assertThat(prompt).contains("수지님");
            assertThat(prompt).doesNotContain("{name");
        }
    }

    @Nested
    @DisplayName("감정 커버리지")
    class EmotionCoverage {

        // 개선 전 [응답 구성]은 감정별 마무리 갈래를 열거했으나, 개선본은 응답의 무게에 따라 길이·구성을
        // 정하는 일반 원칙으로 바뀌어 감정별 갈래 열거가 사라졌다. 그래서 "감정마다 마무리 규칙이 있는지"는
        // 더 이상 프롬프트에서 검증할 수 없다. 남은 최소 안전장치로, EmotionType에 값이 추가됐을 때 그 감정이
        // 프롬프트(예시 태그·질문 예외 목록 등) 어디에도 언급되지 않는 상태만 막는다.
        @ParameterizedTest
        @EnumSource(EmotionType.class)
        @DisplayName("모든 감정이 시스템 프롬프트 어딘가에 언급된다")
        void every_emotion_is_mentioned(EmotionType emotionType) {
            String name = emotionType.getDisplayName();

            for (ToneType toneType : ToneType.values()) {
                assertThat(promptBuilder.systemPromptBuild("용준", toneType))
                    .as("%s(%s)가 시스템 프롬프트에 전혀 언급되지 않습니다", emotionType, toneType)
                    .contains(name);
            }
        }
    }
}
