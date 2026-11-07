package com.flowershop.common.util;

/**
 * Che dữ liệu nhạy cảm trước khi ghi log hoặc trả ra ngoài.
 * Sẽ dùng nhiều ở các giai đoạn sau (SĐT người nhận hoa, email, số tài khoản thanh toán).
 * Ví dụ: log.info("Order for phone={}", DataMasking.maskTail(phone, 3)) -> *******789
 */
public final class DataMasking {

    private DataMasking() {
    }

    public static String maskTail(String value, int visibleTail) {
        if (value == null) {
            return null;
        }
        int len = value.length();
        if (len <= visibleTail) {
            return "*".repeat(len);
        }
        return "*".repeat(len - visibleTail) + value.substring(len - visibleTail);
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return maskTail(email, 0);
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String shown = local.isEmpty() ? "" : local.substring(0, 1);
        return shown + "***" + email.substring(at);
    }
}
