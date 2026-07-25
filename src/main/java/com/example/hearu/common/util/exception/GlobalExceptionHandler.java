package com.example.hearu.common.util.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    /** Request Param & Path Variable 매핑 실패 오류 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {

        log.warn("Request Param & Path Variable 매핑 오류. 상세 메시지={}", e.getMessage());

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
            .message("잘못된 요청 값 입니다.")
            .build();

        return ResponseEntity.status(400).body(response);
    }

    /** 파라미터 누락 오류 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {

        log.warn("파라미터 누락 오류. 상세 메시지={}", e.getMessage());

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

        // 예외를 던지는 시점에 이미 WARN으로 상세 컨텍스트를 남기므로 여기서는 중복 경고를 피하고,
        // 최종적으로 어떤 응답으로 변환되었는지만 DEBUG로 남긴다.
        log.debug("BusinessException 처리. errorCode={}, status={}, message={}",
                e.getErrorCode(),
                e.getErrorCode().getHttpStatus().value(),
                e.getMessage());

        ErrorResponse response = ErrorResponse.builder()
                .status(e.getErrorCode().getHttpStatus().value())
                .code(e.getErrorCode().getHttpStatus().name())
                .message(e.getMessage())
                .build();

        return ResponseEntity.status(e.getErrorCode().getHttpStatus().value()).body(response);
    }

    /** 지원하지 않는 HTTP 메서드로 요청 (405) */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {

        log.warn("지원하지 않는 HTTP 메서드 요청. 상세 메시지={}", e.getMessage());

        ErrorResponse response = ErrorResponse.builder()
            .status(405)
            .code("METHOD_NOT_ALLOWED")
            .message("지원하지 않는 요청 방식입니다.")
            .build();

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    /** 매핑된 핸들러가 없는 경로 요청 (404) */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException e) {

        log.warn("존재하지 않는 경로 요청. 상세 메시지={}", e.getMessage());

        ErrorResponse response = ErrorResponse.builder()
            .status(404)
            .code("NOT_FOUND")
            .message("요청하신 리소스를 찾을 수 없습니다.")
            .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
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