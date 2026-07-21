package com.example.hearu.common.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

import java.util.Map;

/**
 * 요청 스레드의 MDC(requestId, userId)를 {@code @Async} 워커 스레드로 전파한다.
 *
 * <p>MDC는 ThreadLocal 기반이라 기본적으로 스레드를 넘어가지 않는다. 이 데코레이터가 없으면
 * 비동기로 처리되는 AI 응답 로그에 requestId/userId가 남지 않아, 어떤 요청에서 시작된
 * 작업인지 추적할 수 없다.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @NonNull
    @Override
    public Runnable decorate(@NonNull Runnable runnable) {
        // decorate()는 작업을 "제출하는" 스레드(=요청 스레드)에서 실행되므로
        // 이 시점에 요청의 MDC를 스냅샷으로 확보한다.
        Map<String, String> submitterContext = MDC.getCopyOfContextMap();

        return () -> {
            // 스레드 풀의 워커는 재사용되므로, 실행 전 상태를 보관했다가 종료 시 되돌린다.
            // 되돌리지 않으면 이 작업의 MDC가 다음 작업 로그에 잘못 섞여 들어간다.
            Map<String, String> originalContext = MDC.getCopyOfContextMap();
            try {
                setContextOrClear(submitterContext);
                runnable.run();
            } finally {
                setContextOrClear(originalContext);
            }
        };
    }

    /**
     * MDC.setContextMap은 null을 허용하지 않으므로, 빈 컨텍스트는 clear로 처리한다.
     */
    private void setContextOrClear(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
