package com.example.hearu.common.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.example.hearu.common.logging.MdcTaskDecorator;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(30);
        executor.setThreadNamePrefix("Async-");

        // 요청 스레드의 MDC(requestId, userId)를 워커 스레드로 전파한다.
        // initialize() 이전에 설정해야 적용된다.
        executor.setTaskDecorator(new MdcTaskDecorator());

        // 종료 시 큐에 남은 작업까지 처리한다. 기본값(false)이면 shutdownNow()로 폐기되어 재배포마다 유실된다.
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 위 대기의 상한. compose의 stop_grace_period가 이 값보다 커야 SIGKILL이 먼저 오지 않는다.
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }
}
