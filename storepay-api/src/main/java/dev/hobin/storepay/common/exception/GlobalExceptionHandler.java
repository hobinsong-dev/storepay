package dev.hobin.storepay.common.exception;

import dev.hobin.storepay.common.response.ApiResponse;
import dev.hobin.storepay.common.response.ErrorBody;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("business error: code={}, message={}", errorCode.getCode(), e.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(ErrorBody.of(errorCode.getCode(), e.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorBody.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(it -> new ErrorBody.FieldError(it.getField(), it.getDefaultMessage()))
                .toList();
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(
                        ErrorBody.of(errorCode.getCode(), errorCode.getMessage(), fieldErrors)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("unexpected error", e);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(ErrorBody.of(errorCode.getCode(), errorCode.getMessage())));
    }
}
