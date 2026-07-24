package com.example.hearu.auth.infrastructure.provider;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * JWKS 조회 전용 RestTemplate 생성기.
 *
 * <p>NimbusJwtDecoder의 기본 RestTemplate은 타임아웃이 없어 외부 IdP의 JWKS 엔드포인트가
 * 지연되면 요청 스레드가 무한 대기한다. 톰캣 스레드 풀이 고갈되면 서비스 전체 장애로
 * 번지므로 상한을 둔다.
 */
final class JwksRestTemplateFactory {

    // TLS 핸드셰이크는 read timeout에 걸리므로 connect보다 여유를 둔다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private JwksRestTemplateFactory() {
    }

    static RestTemplate create() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return new RestTemplate(factory);
    }
}
