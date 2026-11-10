package com.flowershop.common.security;

public enum UserType {
    /** Nhân viên cửa hàng, đăng nhập bằng username. */
    STAFF,
    /** Khách hàng, đăng nhập bằng số điện thoại, gắn với một hồ sơ bên customer-service. */
    CUSTOMER,
    /** Service gọi service (không phải người). Ví dụ identity-service gọi customer-service lúc khách đăng ký. */
    SERVICE
}
