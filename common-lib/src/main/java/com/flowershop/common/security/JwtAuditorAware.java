package com.flowershop.common.security;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/**
 * Điền created_by / updated_by bằng claim sub của JWT (public id của tài khoản, hoặc tên service).
 * Không có người đăng nhập (job nội bộ, migration dữ liệu...) thì ghi "system".
 *
 * Vì sao là sub (UUID) mà không phải username? sub không bao giờ đổi và không bị cấp lại cho người khác,
 * còn username có thể đổi. Muốn biết sub là ai thì tra ở identity-service.
 */
public class JwtAuditorAware implements AuditorAware<String> {

    public static final String SYSTEM = "system";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token && token.getName() != null) {
            return Optional.of(token.getName());
        }
        return Optional.of(SYSTEM);
    }
}
