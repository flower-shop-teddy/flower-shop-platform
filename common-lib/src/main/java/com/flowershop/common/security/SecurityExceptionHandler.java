package com.flowershop.common.security;

import com.flowershop.common.api.ApiResponse;
import com.flowershop.common.error.CommonErrorCode;
import com.flowershop.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @PreAuthorize từ chối thì ném AccessDeniedException NGAY TRONG controller.
 * Nếu không có class này, handler bắt Exception của GlobalExceptionHandler sẽ biến nó thành 500.
 *
 * @Order cao hơn GlobalExceptionHandler: Spring duyệt các @RestControllerAdvice theo thứ tự,
 * advice đầu tiên có handler khớp sẽ thắng (kể cả khi đó chỉ là handler Exception chung chung).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnClass(name = "org.springframework.security.access.AccessDeniedException")
public class SecurityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied by method security");
        return build(CommonErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return build(CommonErrorCode.UNAUTHORIZED);
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode ec) {
        return ResponseEntity.status(ec.status()).body(ApiResponse.error(ec.code(), ec.defaultMessage(), null));
    }
}
