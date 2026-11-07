package com.flowershop.common.error;

import com.flowershop.common.api.ApiResponse;
import com.flowershop.common.api.FieldViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Chuẩn ngân hàng: KHÔNG BAO GIỜ trả stack trace, tên class, câu SQL ra ngoài.
 * Chi tiết lỗi chỉ ghi vào log (kèm correlationId), client chỉ nhận mã lỗi + thông điệp an toàn.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode ec = ex.getErrorCode();
        log.warn("Business error code={} message={}", ec.code(), ex.getMessage());
        return ResponseEntity.status(ec.status()).body(ApiResponse.error(ec.code(), ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<FieldViolation>>> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(CommonErrorCode.VALIDATION_FAILED, violations);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleMalformed(Exception ex) {
        log.warn("Malformed request: {}", ex.getClass().getSimpleName());
        return build(CommonErrorCode.MALFORMED_REQUEST, null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return build(CommonErrorCode.CONCURRENT_MODIFICATION, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return build(CommonErrorCode.RESOURCE_NOT_FOUND, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex); // stack trace chỉ nằm trong log nội bộ
        return build(CommonErrorCode.INTERNAL_ERROR, null);
    }

    private <T> ResponseEntity<ApiResponse<T>> build(ErrorCode ec, T details) {
        return ResponseEntity.status(ec.status()).body(ApiResponse.error(ec.code(), ec.defaultMessage(), details));
    }
}
