package com.flowershop.common.security;

import java.util.List;

/**
 * Người đang gọi API, đọc từ access token.
 *
 * @param subject    claim sub: public id (UUID) của tài khoản, hoặc tên service nếu userType = SERVICE
 * @param customerId chỉ có khi userType = CUSTOMER
 */
public record AuthenticatedUser(
        String subject,
        UserType userType,
        Long customerId,
        List<String> roles,
        List<String> permissions
) {
    public boolean isCustomer() {
        return userType == UserType.CUSTOMER;
    }
}
