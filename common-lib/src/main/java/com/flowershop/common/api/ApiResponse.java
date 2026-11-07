package com.flowershop.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flowershop.common.web.CorrelationId;
import org.slf4j.MDC;

import java.time.Instant;

/**
 * Format response thống nhất cho MỌI API của MỌI service.
 * code = "00" là thành công (quy ước hay gặp ở ngân hàng), còn lại là mã lỗi.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String correlationId,
        Instant timestamp
) {
    public static final String SUCCESS_CODE = "00";

    public static <T> ApiResponse<T> success(T data) {
        return success("Thành công", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(SUCCESS_CODE, message, data, currentCorrelationId(), Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message, T details) {
        return new ApiResponse<>(code, message, details, currentCorrelationId(), Instant.now());
    }

    private static String currentCorrelationId() {
        return MDC.get(CorrelationId.MDC_KEY);
    }
}
