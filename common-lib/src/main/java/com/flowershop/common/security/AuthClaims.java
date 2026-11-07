package com.flowershop.common.security;

/**
 * Tên các claim trong access token (JWT) do identity-service cấp.
 * Đặt ở common-lib để bên CẤP token (identity) và bên ĐỌC token (mọi service) dùng chung một nguồn,
 * tránh mỗi nơi gõ tay một tên rồi lệch nhau.
 *
 * Ví dụ payload:
 * <pre>
 * { "iss": "flower-shop-identity", "aud": ["flower-shop-api"], "sub": "6f1c...-uuid",
 *   "user_type": "STAFF", "roles": ["SHOP_MANAGER"], "perms": ["flower:write", "customer:read"],
 *   "iat": 1762500000, "exp": 1762500900, "jti": "..." }
 * </pre>
 */
public final class AuthClaims {

    /** STAFF / CUSTOMER / SERVICE, xem {@link UserType}. */
    public static final String USER_TYPE = "user_type";
    /** Danh sách role, chỉ để hiển thị/ghi log. Code kiểm tra quyền dựa trên {@link #PERMS}. */
    public static final String ROLES = "roles";
    /** Danh sách permission dạng resource:action, ví dụ flower:write. */
    public static final String PERMS = "perms";
    /** Id hồ sơ khách hàng bên customer-service, chỉ có khi user_type = CUSTOMER. */
    public static final String CUSTOMER_ID = "customer_id";
    /** true khi user phải đổi mật khẩu trước (lúc này perms rỗng). */
    public static final String PWD_CHANGE_REQUIRED = "pwd_change_required";

    /** Quy ước của Spring Security: authority bắt đầu bằng ROLE_ là role. */
    public static final String ROLE_PREFIX = "ROLE_";

    private AuthClaims() {
    }
}
