package com.girisk.auth;

import com.girisk.auth.model.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expiryHours;

    public JwtTokenProvider(
            @Value("${girisk.jwt.secret}") String secret,
            @Value("${girisk.jwt.expiry-hours:8}") long expiryHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryHours = expiryHours;
    }

    public String createToken(SysUser user, Collection<String> roles, Collection<String> permissions) {
        Instant now = Instant.now();
        List<String> roleList = roles == null ? List.of() : List.copyOf(roles);
        List<String> permList = permissions == null ? List.of() : List.copyOf(permissions);
        String primary = roleList.isEmpty() ? user.role() : roleList.get(0);
        String scope = user.operatorScope() == null || user.operatorScope().isBlank() ? "*" : user.operatorScope();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.username())
                .claim("role", primary)
                .claim("roles", roleList)
                .claim("perms", permList)
                .claim("displayName", user.displayName())
                .claim("operatorScope", scope)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiryHours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public List<String> rolesFromClaims(Claims claims) {
        Object raw = claims.get("roles");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(String::valueOf).toList();
        }
        String role = claims.get("role", String.class);
        return role == null || role.isBlank() ? List.of() : List.of(role);
    }

    public List<String> permsFromClaims(Claims claims) {
        Object raw = claims.get("perms");
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
