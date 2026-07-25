package com.example.hearu.auth.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import com.example.hearu.auth.service.RefreshTokenService;
import com.example.hearu.common.client.slack.SlackNotifierClient;

/**
 * 스케줄러 메서드에 @Scheduled와 @Retryable을 함께 두었을 때, 재시도 프록시가 적용되면서도
 * 스케줄 등록이 정상적으로 이뤄지는지는 컴파일·단위 테스트로는 드러나지 않는다.
 * 실제 스프링 컨텍스트로 두 애노테이션의 공존 배선을 검증한다.
 */
@DisplayName("RefreshTokenCleanupScheduler 배선 (@Scheduled + @Retryable 공존)")
class RefreshTokenCleanupSchedulerWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withBean(RefreshTokenService.class, () -> mock(RefreshTokenService.class))
            .withBean(SlackNotifierClient.class, () -> mock(SlackNotifierClient.class))
            .withBean(RefreshTokenCleanupScheduler.class)
            .withPropertyValues("scheduler.refresh-token.cleanup.cron=0 0 3 * * *");

    @Test
    @DisplayName("컨텍스트가 정상 기동된다")
    void contextLoads() {
        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("@Retryable 프록시가 적용된다")
    void retryProxyApplied() {
        contextRunner.run(context -> {
            RefreshTokenCleanupScheduler bean = context.getBean(RefreshTokenCleanupScheduler.class);
            assertThat(AopUtils.isAopProxy(bean)).isTrue();
        });
    }

    @Test
    @DisplayName("프록시된 빈에서도 @Scheduled 태스크가 등록된다")
    void scheduledTaskRegistered() {
        contextRunner.run(context -> {
            ScheduledAnnotationBeanPostProcessor processor =
                    context.getBean(ScheduledAnnotationBeanPostProcessor.class);
            assertThat(processor.getScheduledTasks()).isNotEmpty();
        });
    }

    @Configuration
    @EnableRetry
    @EnableScheduling
    static class TestConfig {
    }
}
