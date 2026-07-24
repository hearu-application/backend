package com.example.hearu.common.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.example.hearu.common.config.AsyncConfig;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MdcTaskDecorator")
class MdcTaskDecoratorTest {

    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUp() {
        // 워커 스레드 재사용 상황을 재현하기 위해 단일 스레드 풀을 사용한다.
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
        MDC.clear();
    }

    /**
     * 비동기 작업 내부에서 관측된 MDC 스냅샷을 반환한다.
     */
    private Map<String, String> captureMdcInAsyncTask() throws InterruptedException {
        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        AtomicReference<String> workerThreadName = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(() -> {
            captured.set(MDC.getCopyOfContextMap());
            workerThreadName.set(Thread.currentThread().getName());
            latch.countDown();
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        // 실제로 다른 스레드에서 실행되었는지 확인 (같은 스레드면 테스트가 무의미해진다)
        assertThat(workerThreadName.get()).isNotEqualTo(Thread.currentThread().getName());

        return captured.get();
    }

    @Nested
    @DisplayName("MDC 전파")
    class Propagation {

        @Test
        @DisplayName("요청 스레드의 MDC가 비동기 워커 스레드로 전파된다")
        void propagatesMdcToWorkerThread() throws InterruptedException {
            // given
            MDC.put("requestId", "abc12345");
            MDC.put("userId", "42");

            // when
            Map<String, String> capturedInWorker = captureMdcInAsyncTask();

            // then
            assertThat(capturedInWorker)
                    .containsEntry("requestId", "abc12345")
                    .containsEntry("userId", "42");
        }

        @Test
        @DisplayName("요청 스레드에 MDC가 없으면 워커 스레드에도 전파되지 않는다")
        void propagatesNothingWhenSubmitterHasNoMdc() throws InterruptedException {
            // given
            MDC.clear();

            // when
            Map<String, String> capturedInWorker = captureMdcInAsyncTask();

            // then
            assertThat(capturedInWorker).isNullOrEmpty();
        }
    }

    @Nested
    @DisplayName("AsyncConfig 배선")
    class Wiring {

        @Test
        @DisplayName("AsyncConfig가 생성한 실제 executor에도 MDC 전파가 적용되어 있다")
        void productionExecutorPropagatesMdc() throws InterruptedException {
            // given: 테스트용이 아닌 실제 운영 설정으로 만들어진 executor
            Executor productionExecutor = new AsyncConfig().taskExecutor();
            MDC.put("requestId", "wired123");

            AtomicReference<String> capturedRequestId = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            // when
            productionExecutor.execute(() -> {
                capturedRequestId.set(MDC.get("requestId"));
                latch.countDown();
            });

            // then
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(capturedRequestId.get()).isEqualTo("wired123");
        }
    }

    @Nested
    @DisplayName("워커 스레드 정리")
    class Cleanup {

        @Test
        @DisplayName("이전 작업의 MDC가 같은 워커 스레드의 다음 작업으로 새지 않는다")
        void doesNotLeakMdcToNextTaskOnReusedThread() throws InterruptedException {
            // given: 첫 번째 작업을 MDC가 채워진 상태로 실행
            MDC.put("requestId", "first-request");
            MDC.put("userId", "111");
            captureMdcInAsyncTask();

            // when: MDC 없는 상태에서 두 번째 작업을 같은 워커 스레드에 제출
            MDC.clear();
            Map<String, String> capturedInSecondTask = captureMdcInAsyncTask();

            // then: 첫 번째 요청의 컨텍스트가 남아있으면 안 된다
            assertThat(capturedInSecondTask).isNullOrEmpty();
        }

        @Test
        @DisplayName("비동기 작업 실행이 요청 스레드의 MDC를 훼손하지 않는다")
        void doesNotDisturbSubmitterMdc() throws InterruptedException {
            // given
            MDC.put("requestId", "abc12345");

            // when
            captureMdcInAsyncTask();

            // then
            assertThat(MDC.get("requestId")).isEqualTo("abc12345");
        }
    }
}
