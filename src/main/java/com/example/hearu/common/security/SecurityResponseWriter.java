package com.example.hearu.common.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

public class SecurityResponseWriter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private SecurityResponseWriter() {}

    public static void writeJsonErrorResponse(
        HttpServletResponse response,
        SecurityServletErrorCode errorCode
    ) throws IOException {

        response.setStatus(errorCode.getHttpStatus());
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", errorCode.getHttpStatus());
        body.put("code", errorCode.name());
        body.put("message", errorCode.getMessage());

        String jsonBody = objectMapper.writeValueAsString(body);

        response.getWriter().write(jsonBody);
    }
}
