package com.flowershop.common.client;

import java.time.Duration;
import java.util.Objects;

/**
 * Địa chỉ + timeout của một service đích.
 * Timeout BẮT BUỘC: không có timeout thì service đích treo là service gọi treo theo (hết thread, sập dây chuyền).
 *
 * @param name           tên service đích, chỉ dùng để ghi log và trong {@link RemoteServiceException}
 * @param baseUrl        vd http://customer-service (tên trên Eureka, RestClient @LoadBalanced tự tra địa chỉ)
 * @param connectTimeout thời gian tối đa để mở kết nối
 * @param readTimeout    thời gian tối đa chờ service đích trả lời
 */
public record ServiceEndpoint(String name, String baseUrl, Duration connectTimeout, Duration readTimeout) {

    public ServiceEndpoint {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(baseUrl, "baseUrl");
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
    }

    private static void requirePositive(Duration timeout, String field) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(field + " phải lớn hơn 0 (không cho phép chờ vô hạn)");
        }
    }
}
