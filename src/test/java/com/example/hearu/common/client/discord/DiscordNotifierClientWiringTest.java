package com.example.hearu.common.client.discord;

import com.example.hearu.ai.response.infrastructure.client.OpenAiClient;
import com.example.hearu.common.config.RestClientConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RestClient 빈이 두 개(aiRestClient, discordRestClient)가 되면서 타입만으로는 주입 대상이
 * 결정되지 않는다. 잘못 주입되면 타임아웃 설정이 어긋나는데 컴파일 시점에는 드러나지 않으므로,
 * 실제 스프링 컨텍스트로 배선을 검증한다.
 */
@DisplayName("RestClient 빈 주입 배선")
class DiscordNotifierClientWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RestClientConfig.class)
            .withBean(DiscordNotifierClient.class)
            .withBean(OpenAiClient.class)
            .withPropertyValues(
                    "discord.webhook.url=http://localhost/webhook",
                    "llm.completion-url=http://localhost/llm",
                    "llm.api-key=test-key",
                    "llm.model=gpt-5.6-luna"
            );

    @Test
    @DisplayName("빈이 모호하지 않게 생성된다")
    void contextLoadsWithoutAmbiguity() {
        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("DiscordNotifierClient에 discordRestClient 빈이 주입된다")
    void discordClientGetsDiscordRestClient() {
        contextRunner.run(context -> {
            DiscordNotifierClient client = context.getBean(DiscordNotifierClient.class);
            RestClient expected = (RestClient) context.getBean("discordRestClient");

            assertThat(ReflectionTestUtils.getField(client, "discordRestClient"))
                    .isSameAs(expected);
        });
    }

    @Test
    @DisplayName("OpenAiClient에 aiRestClient 빈이 주입된다")
    void openAiClientGetsAiRestClient() {
        contextRunner.run(context -> {
            OpenAiClient client = context.getBean(OpenAiClient.class);
            RestClient expected = (RestClient) context.getBean("aiRestClient");

            assertThat(ReflectionTestUtils.getField(client, "aiRestClient"))
                    .isSameAs(expected);
        });
    }
}
