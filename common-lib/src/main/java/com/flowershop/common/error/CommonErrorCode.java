package com.flowershop.common.error;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {
    VALIDATION_FAILED("COM-400-001", HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    MALFORMED_REQUEST("COM-400-002", HttpStatus.BAD_REQUEST, "Request sai định dạng"),
    RESOURCE_NOT_FOUND("COM-404-001", HttpStatus.NOT_FOUND, "Không tìm thấy đường dẫn"),
    CONCURRENT_MODIFICATION("COM-409-001", HttpStatus.CONFLICT,
            "Dữ liệu đã bị người khác thay đổi, vui lòng tải lại và thử lại"),
    INTERNAL_ERROR("COM-500-001", HttpStatus.INTERNAL_SERVER_ERROR,
            "Hệ thống đang gặp sự cố, vui lòng thử lại sau");

    private final String code;
    private final HttpStatus status;
    private final String defaultMessage;

    CommonErrorCode(String code, HttpStatus status, String defaultMessage) {
        this.code = code;
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    @Override public String code() { return code; }
    @Override public HttpStatus status() { return status; }
    @Override public String defaultMessage() { return defaultMessage; }
}
