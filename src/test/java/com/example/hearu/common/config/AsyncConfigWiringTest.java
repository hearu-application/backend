package com.example.hearu.common.config;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 거부 핸들러는 큐가 실제로 포화돼야 호출되므로 평소 테스트에서 한 번도 실행되지 않는다.
 * 핸들러가 붙었는지, 그리고 기본 정책(AbortPolicy)의 "거부 시 예외를 던진다"가 유지되는지를
 * 컨텍스트에서 직접 확인한다. 예외를 삼키면 큐 포화가 조용한 유실로 바뀌는데,
 * 컴파일·기존 테스트로는 드러나지 않는다.
 */
@DisplayName("비동기 실행기 배선")
class AsyncConfigWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(AsyncConfig.class);

    @Test
    @DisplayName("기본 AbortPolicy가 아닌 거부 핸들러가 설정된다")
    void rejectionHandlerIsReplaced() {
        contextRunner.run(context ->
            assertThat(threadPoolOf(context.getBean("taskExecutor", Executor.class))
                .getRejectedExecutionHandler())
                .isNotInstanceOf(ThreadPoolExecutor.AbortPolicy.class));
    }

    @Test
    @DisplayName("작업이 거부되면 기본 정책과 동일하게 예외를 던진다")
    void rejectionStillThrows() {
        contextRunner.run(context -> {
            ThreadPoolExecutor pool = threadPoolOf(context.getBean("taskExecutor", Executor.class));

            assertThatThrownBy(() ->
                pool.getRejectedExecutionHandler().rejectedExecution(() -> { }, pool))
                .isInstanceOf(RejectedExecutionException.class);
        });
    }

    private ThreadPoolExecutor threadPoolOf(Executor executor) {
        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        return ((ThreadPoolTaskExecutor) executor).getThreadPoolExecutor();
    }
}
