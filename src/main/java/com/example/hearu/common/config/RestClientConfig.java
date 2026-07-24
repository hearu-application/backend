package com.example.hearu.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient aiRestClient() {
        SimpleClientHttpRequestFactory factory =
            new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(6_000);

        return RestClient.builder()
            .requestFactory(factory)
            .build();
    }

    /**
     * Slack 알림 전송 전용 RestClient.
     *
     * <p>타임아웃이 없으면 Slack 지연 시 호출 스레드가 무한 대기한다. 특히 이 클라이언트는
     * 스케줄러의 장애 복구(@Recover) 경로에서 호출되므로, 알림을 보내려다 스케줄러 스레드가
     * 묶이는 상황을 막아야 한다. 알림은 실패해도 본 로직에 영향이 없으므로 상한을 짧게 둔다.
     */
    @Bean
    public RestClient slackRestClient() {
        SimpleClientHttpRequestFactory factory =
            new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);

        return RestClient.builder()
            .requestFactory(factory)
            .build();
    }
}
