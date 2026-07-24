package com.example.hearu.common.client.slack;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import com.example.hearu.common.logging.LogMasker;
import com.example.hearu.common.util.SafeBody;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotifierClient {

    // RestClientConfig의 타임아웃이 설정된 빈을 주입받는다.
    // RestClient.create()로 직접 만들면 타임아웃이 없어 호출 스레드가 무한 대기할 수 있다.
    private final RestClient slackRestClient;

    @Value("${slack.webhook.url}")
    private String webhookUrl;

    public void sendNotification(String message) {
        log.debug("[NOTIFIER] Slack 알림 전송 시작. message={}", LogMasker.textLength(message));
        try {
            slackRestClient.post()
                .uri(webhookUrl)
                .body(Map.of("text", message))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    log.warn(
                        "[NOTIFIER][4xx] status={}, body={}",
                        res.getStatusCode(),
                        SafeBody.read(res)
                    );
                    throw new HttpClientErrorException(res.getStatusCode());
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    log.error(
                        "[NOTIFIER][5xx] status={}, body={}",
                        res.getStatusCode(),
                        SafeBody.read(res)
                    );
                    throw new HttpServerErrorException(res.getStatusCode());
                })
                .toBodilessEntity();

            log.debug("[NOTIFIER] Slack 알림 전송 완료");
        } catch (Exception e) {
            log.error("Slack 알림 전송 실패", e);
        }
    }
}
