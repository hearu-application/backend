package com.example.hearu.common.client.discord;

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
public class DiscordNotifierClient {

    // Discord Webhook의 content 상한. 넘기면 400으로 거부되므로 보내기 전에 잘라낸다.
    // 알림 메시지에는 외부 예외 메시지가 그대로 실려 길이를 호출부가 보장할 수 없다.
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final String TRUNCATION_SUFFIX = "...(truncated)";

    // RestClientConfig의 타임아웃이 설정된 빈을 주입받는다.
    // RestClient.create()로 직접 만들면 타임아웃이 없어 호출 스레드가 무한 대기할 수 있다.
    private final RestClient discordRestClient;

    @Value("${discord.webhook.url}")
    private String webhookUrl;

    public void sendNotification(String message) {
        String content = truncate(message);

        log.debug("[NOTIFIER] Discord 알림 전송 시작. message={}", LogMasker.textLength(content));
        try {
            discordRestClient.post()
                .uri(webhookUrl)
                .body(Map.of("content", content))
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

            log.debug("[NOTIFIER] Discord 알림 전송 완료");
        } catch (Exception e) {
            log.error("Discord 알림 전송 실패", e);
        }
    }

    private String truncate(String message) {
        if (message == null || message.length() <= MAX_CONTENT_LENGTH) {
            return message;
        }

        return message.substring(0, MAX_CONTENT_LENGTH - TRUNCATION_SUFFIX.length())
            + TRUNCATION_SUFFIX;
    }
}
