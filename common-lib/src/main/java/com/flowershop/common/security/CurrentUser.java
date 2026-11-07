package com.flowershop.common.security;

import com.flowershop.common.error.BusinessException;
import com.flowershop.common.error.CommonErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/**
 * Lấy thông tin người đang gọi API từ SecurityContext (đã được Spring Security điền sau khi verify JWT).
 * Dùng ở service/controller thay vì nhận userId/customerId từ request body hay URL:
 * dữ liệu "tôi là ai" chỉ được tin khi nó nằm trong token đã ký.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthenticatedUser> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)) {
            return Optional.empty();
        }
        Jwt jwt = token.getToken();
        String type = jwt.getClaimAsString(AuthClaims.USER_TYPE);
        Object rawCustomerId = jwt.getClaim(AuthClaims.CUSTOMER_ID);
        Long customerId = rawCustomerId instanceof Number n ? n.longValue() : null;
        return Optional.of(new AuthenticatedUser(
                jwt.getSubject(),
                type == null ? null : UserType.valueOf(type),
                customerId,
                JwtAuthConverter.listClaim(jwt, AuthClaims.ROLES),
                JwtAuthConverter.listClaim(jwt, AuthClaims.PERMS)));
    }

    public static AuthenticatedUser require() {
        return get().orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }

    /**
     * Id hồ sơ khách hàng của người đang đăng nhập. Dùng cho các API /me.
     * Nhân viên gọi API /me thì bị từ chối: nhân viên không có hồ sơ khách hàng.
     */
    public static long requireCustomerId() {
        AuthenticatedUser user = require();
        if (!user.isCustomer() || user.customerId() == null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return user.customerId();
    }
}
