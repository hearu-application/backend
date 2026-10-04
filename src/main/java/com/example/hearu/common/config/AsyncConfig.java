package com.example.hearu.common.config;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.example.hearu.common.logging.MdcTaskDecorator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
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

        // 위 대기의 상한. AI 응답 1건의 최악 처리 시간은 2 × (connect 3s + read 20s) + backoff 1s ≈ 47s다.
        // compose의 stop_grace_period가 이 값보다 커야 SIGKILL이 먼저 오지 않는다.
        executor.setAwaitTerminationSeconds(50);

        // 기본 정책(AbortPolicy)은 예외만 던지고 거부 사실은 어디에도 남기지 않는다.
        // 거부 시점을 남기되, 예외는 동일하게 던져 동작은 바꾸지 않는다.
        executor.setRejectedExecutionHandler((task, threadPoolExecutor) -> {
            log.error("[Async][Rejected] 스레드 풀 포화로 비동기 작업이 거부되었습니다. "
                    + "activeCount={}, poolSize={}, queueSize={}",
                threadPoolExecutor.getActiveCount(),
                threadPoolExecutor.getPoolSize(),
                threadPoolExecutor.getQueue().size());
            throw new RejectedExecutionException("Async task rejected: thread pool saturated");
        });

        executor.initialize();
        return executor;
    }
}
