package com.flowershop.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowershop.common.api.ApiResponse;
import com.flowershop.common.error.CommonErrorCode;
import com.flowershop.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Lỗi 401/403 xảy ra trong filter của Spring Security, TRƯỚC khi vào controller,
 * nên GlobalExceptionHandler không bắt được. Class này trả về cùng format ApiResponse
 * để client chỉ phải xử lý một kiểu body lỗi.
 *
 * Không trả lý do chi tiết (token hết hạn hay sai chữ ký...) cho client, chỉ ghi log.
 */
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiSecurityErrorHandler.class);

    private final ObjectMapper objectMapper;

    public ApiSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        log.warn("Unauthenticated request {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getClass().getSimpleName());
        // Chuẩn OAuth2 (RFC 6750): báo client biết cần gửi Bearer token
        response.setHeader("WWW-Authenticate", "Bearer");
        write(response, CommonErrorCode.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        log.warn("Access denied {} {}", request.getMethod(), request.getRequestURI());
        write(response, CommonErrorCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, ErrorCode ec) throws IOException {
        response.setStatus(ec.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(ec.code(), ec.defaultMessage(), null));
    }
}
