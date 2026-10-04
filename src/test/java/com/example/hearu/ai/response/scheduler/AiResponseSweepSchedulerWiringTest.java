package com.example.hearu.ai.response.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import com.example.hearu.ai.response.service.AiResponseService;
import com.example.hearu.common.client.discord.DiscordNotifierClient;

/**
 * cron과 stale-after가 application.yml 프로퍼티에서 바인딩되는지는 단위 테스트로 드러나지 않는다.
 * 특히 stale-after는 "10m" 같은 문자열이 Duration으로 변환돼야 하므로 실제 컨텍스트로 확인한다.
 */
@DisplayName("AiResponseSweepScheduler 배선 (프로퍼티 바인딩 + @Scheduled 등록)")
class AiResponseSweepSchedulerWiringTest {

    // SpringApplication은 Boot의 변환 서비스("10m" → Duration)를 빈 팩토리에 등록하지만 러너는 하지 않으므로 맞춰 준다.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory()
                .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(TestConfig.class)
            .withBean(AiResponseService.class, () -> mock(AiResponseService.class))
            .withBean(DiscordNotifierClient.class, () -> mock(DiscordNotifierClient.class))
            .withBean(AiResponseSweepScheduler.class)
            .withPropertyValues(
                "scheduler.ai-response.sweep.cron=0 0 * * * *",
                "scheduler.ai-response.sweep.stale-after=10m");

    @Test
    @DisplayName("컨텍스트가 정상 기동된다 (stale-after가 Duration으로 변환된다)")
    void contextLoads() {
        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("@Scheduled 태스크가 등록된다")
    void scheduledTaskRegistered() {
        contextRunner.run(context -> {
            ScheduledAnnotationBeanPostProcessor processor =
                    context.getBean(ScheduledAnnotationBeanPostProcessor.class);
            assertThat(processor.getScheduledTasks()).isNotEmpty();
        });
    }

    @Configuration
    @EnableScheduling
    static class TestConfig {
    }
}
