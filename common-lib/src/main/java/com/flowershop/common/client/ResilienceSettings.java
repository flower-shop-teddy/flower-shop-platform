package com.flowershop.common.client;

import java.time.Duration;

/**
 * Cấu hình circuit breaker + retry cho lời gọi giữa các service.
 *
 * @param windowSize          circuit breaker xét N lời gọi gần nhất
 * @param minimumCalls        cần ít nhất N lời gọi mới tính tỷ lệ lỗi (tránh ngắt mạch vì 1/1 lỗi lúc vừa khởi động)
 * @param failureRatePercent  tỷ lệ lỗi (%) để ngắt mạch
 * @param openDuration        ngắt mạch bao lâu rồi mới cho vài lời gọi thử (half-open)
 * @param halfOpenCalls       số lời gọi thử ở trạng thái half-open
 * @param maxAttempts         tổng số lần gọi tối đa, tính cả lần đầu (chỉ áp dụng cho lời gọi idempotent)
 * @param initialBackoff      chờ trước lần thử lại đầu tiên, sau đó gấp đôi, có ngẫu nhiên ±50%
 */
public record ResilienceSettings(int windowSize, int minimumCalls, float failureRatePercent, Duration openDuration,
                                 int halfOpenCalls, int maxAttempts, Duration initialBackoff) {

    /**
     * Mặc định: ngắt mạch khi ≥50% của 20 lời gọi gần nhất lỗi (cần ít nhất 10), ngắt 15 giây.
     * Thử lại tối đa 3 lần, chờ ~200ms rồi ~400ms. Trường hợp xấu nhất một lời gọi idempotent mất
     * 3 × readTimeout + ~600ms, nên readTimeout của service đích phải nhỏ hơn hẳn timeout của client gọi mình.
     */
    public static ResilienceSettings defaults() {
        return new ResilienceSettings(20, 10, 50f, Duration.ofSeconds(15), 3, 3, Duration.ofMillis(200));
    }
}
