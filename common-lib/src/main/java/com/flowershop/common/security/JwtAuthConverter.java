package com.flowershop.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Biến JWT đã verify thành Authentication của Spring Security.
 * <ul>
 *   <li>perms ["flower:write"] -> authority "flower:write"  => dùng @PreAuthorize("hasAuthority('flower:write')")</li>
 *   <li>roles ["ADMIN"]        -> authority "ROLE_ADMIN"    => hasRole('ADMIN'), chỉ dùng khi thật cần</li>
 * </ul>
 * Mặc định Spring đọc claim "scope" và thêm tiền tố SCOPE_, không hợp với thiết kế RBAC ở đây nên tự viết.
 */
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        for (String perm : listClaim(jwt, AuthClaims.PERMS)) {
            authorities.add(new SimpleGrantedAuthority(perm));
        }
        for (String role : listClaim(jwt, AuthClaims.ROLES)) {
            authorities.add(new SimpleGrantedAuthority(AuthClaims.ROLE_PREFIX + role));
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    static List<String> listClaim(Jwt jwt, String name) {
        List<String> values = jwt.getClaimAsStringList(name);
        return values == null ? List.of() : values;
    }
}
