package com.example.hearu.common.util.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    /** RequestBody Json 매핑 실패 오류 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {

        log.warn("Request Body Json 매핑 오류. 상세 메시지={}", e.getMessage());

        ErrorResponse response = ErrorResponse.builder()
            .status(400)
            .code("BAD_REQUEST")
            .message("잘못된 요청 값 입니다.")
            .build();

        return ResponseEntity.status(400).body(response);
    }

    /** DTO 필드 검증 실패 오류 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {

        log.warn("Request Body - Dto 매핑 오류. 상세 메시지={}", e.getBindingResult().getFieldErrors().getFirst().getDefaultMessage());

        ErrorResponse response = ErrorResponse.builder()
            .status(400)
            .code("BAD_REQUEST")
            .message(e.getBindingResult().getFieldErrors().getFirst().getDefaultMessage())
            .build();

        return ResponseEntity.status(400).body(response);
    }

    /** 파라미터 누락 오류 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {

        ErrorResponse response = ErrorResponse.builder()
            .status(400)
            .code("BAD_REQUEST")
            .message("잘못된 요청 값 입니다.")
            .build();

        return ResponseEntity.status(400).body(response);
    }

    /** 비즈니스 오류 오류 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {

        ErrorResponse response = ErrorResponse.builder()
                .status(e.getErrorCode().getHttpStatus().value())
                .code(e.getErrorCode().getHttpStatus().name())
                .message(e.getMessage())
                .build();

        return ResponseEntity.status(e.getErrorCode().getHttpStatus().value()).body(response);
    }

    /** 외부 API 타임아웃 */
    @ExceptionHandler(ResourceAccessException .class)
    public ResponseEntity<ErrorResponse> handleResourceAccessException (ResourceAccessException e) {

        log.warn("외부 API 타임아웃 발생: {}", e.getMessage());

        ErrorResponse response = ErrorResponse.builder()
            .status(504)
            .code("GATEWAY_TIMEOUT")
            .message("외부 서비스 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.")
            .build();

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response);
    }

    /** 예상 못한 모든 서버 오류 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {

        log.error("예상치 못한 오류. 상세 메시지={}", e.getMessage(), e);

        ErrorResponse response = ErrorResponse.builder()
                .status(500)
                .code("INTERNAL_SERVER_ERROR")
                .message("알 수 없는 오류입니다.")
                .build();

        return ResponseEntity.status(500).body(response);
    }
}