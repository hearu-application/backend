package com.example.hearu.common.response;

// HTTP 상태 코드는 상태 라인에만 둔다. 본문에 중복시키면 두 값을 수동으로 맞춰야 하고,
// 실제로 201/202로 응답하면서 본문에는 200을 담는 불일치가 있었다.
public record ApiResponse<T>(String message, T data) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(message, data);
    }
}
