package com.flowershop.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

/**
 * Phần cấu hình GIỐNG NHAU ở mọi service làm "resource server" (nhận request kèm JWT).
 * Luật URL nào cần quyền gì thì mỗi service tự viết trong SecurityConfig của nó, ví dụ:
 *
 * <pre>
 * ApiSecurity.configure(http, objectMapper);
 * http.authorizeHttpRequests(a -> a
 *         .requestMatchers(HttpMethod.GET, "/api/v1/flowers/**").permitAll()
 *         .anyRequest().authenticated());
 * return http.build();
 * </pre>
 */
public final class ApiSecurity {

    private ApiSecurity() {
    }

    public static HttpSecurity configure(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        ApiSecurityErrorHandler errorHandler = new ApiSecurityErrorHandler(objectMapper);
        return http
                // API chỉ dùng Bearer token, không dùng cookie -> không bị CSRF, không cần session
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtAuthConverter()))
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .addFilterAfter(new UserMdcFilter(), BearerTokenAuthenticationFilter.class);
    }
}
