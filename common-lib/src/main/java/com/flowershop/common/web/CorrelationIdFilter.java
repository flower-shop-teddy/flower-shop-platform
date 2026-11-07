package com.flowershop.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Đọc X-Correlation-Id từ gateway, đưa vào MDC để mọi dòng log đều có mã này.
 * Header từ bên ngoài phải được KIỂM TRA định dạng trước khi ghi vào log,
 * tránh tấn công log injection (chèn ký tự xuống dòng để giả mạo log).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = request.getHeader(CorrelationId.HEADER);
        if (id == null || !VALID_ID.matcher(id).matches()) {
            id = UUID.randomUUID().toString();
        }
        MDC.put(CorrelationId.MDC_KEY, id);
        response.setHeader(CorrelationId.HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY); // bắt buộc dọn, tránh rò sang request khác
        }
    }
}
