package com.example.hearu.common.response;

import org.springframework.http.HttpStatus;

public record ApiResponse<T>(int status, String message, T data) {

    private ApiResponse(HttpStatus status, String message, T data) {
        this(status.value(), message, data);
    }

    public static <T> ApiResponse<T> success(String message, T data, HttpStatus status) {
        return new ApiResponse<>(
                status,
                message,
                data
        );
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return success(message, data, HttpStatus.OK);
    }
}