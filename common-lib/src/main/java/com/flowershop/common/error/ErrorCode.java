package com.flowershop.common.error;

import org.springframework.http.HttpStatus;

/**
 * Mã lỗi chuẩn hóa. Quy ước: <SERVICE>-<HTTP>-<SỐ THỨ TỰ>, ví dụ CAT-404-001.
 * Bộ phận vận hành/CSKH tra mã lỗi là biết lỗi gì, không cần đọc code.
 */
public interface ErrorCode {
    String code();

    HttpStatus status();

    String defaultMessage();
}
