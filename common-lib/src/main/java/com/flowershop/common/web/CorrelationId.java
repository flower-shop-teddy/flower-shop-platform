package com.flowershop.common.web;

/**
 * Correlation ID: một mã gắn theo request đi xuyên qua mọi service.
 * Khi khách khiếu nại, chỉ cần mã này là tra ra toàn bộ log liên quan.
 */
public final class CorrelationId {
    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }
}
