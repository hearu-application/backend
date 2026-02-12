package com.example.hearu.common.util;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.http.client.ClientHttpResponse;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SafeBody {

    private static final int MAX_LENGTH = 500;

    public static String read(ClientHttpResponse response) {
        if (response == null) {
            return "[RESPONSE_NULL]";
        }

        try (InputStream is = response.getBody()) {

            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            if (body.length() > MAX_LENGTH) {
                return body.substring(0, MAX_LENGTH) + "...(truncated)";
            }

            return body;

        } catch (Exception e) {
            return "[FAILED_TO_READ_BODY]";
        }
    }
}
