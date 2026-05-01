package com.qctv1.ai.drama.support;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        if (ex.getCode() >= 500) {
            log.error("[DRAMA_BUSINESS_EXCEPTION] code={}, message={}", ex.getCode(), ex.getMessage(), ex);
        } else {
            log.warn("[DRAMA_BUSINESS_EXCEPTION] code={}, message={}", ex.getCode(), ex.getMessage());
        }
        HttpStatus status = ex.getCode() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidation(Exception ex) {
        log.warn("[DRAMA_VALIDATION_EXCEPTION] message={}", ex.getMessage(), ex);
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, "请求参数不合法"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("[DRAMA_UNHANDLED_EXCEPTION]", ex);
        String message = ex.getMessage() == null ? "短剧服务内部错误" : ex.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(500, message));
    }
}
