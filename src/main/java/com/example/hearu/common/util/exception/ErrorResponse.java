package com.example.hearu.common.util.exception;

import lombok.Builder;

@Builder
public record ErrorResponse(int status, String code, String message) {

}