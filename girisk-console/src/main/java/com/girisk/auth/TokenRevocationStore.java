package com.girisk.auth;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT 吊销：按 jti 黑名单 + 用户「令牌签发下限」（改密/停用后踢下线）。
 * 进程内实现；多副本时请换 Redis（同一 key 空间即可）。
 */
@Component
public class TokenRevocationStore {

    private final Map<String, Instant> revokedJti = new ConcurrentHashMap<>();
    private final Map<String, Instant> userNotBefore = new ConcurrentHashMap<>();

    public void revokeJti(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        revokedJti.put(jti, expiresAt != null ? expiresAt : Instant.now().plusSeconds(86400));
        purgeExpired();
    }

    /** 使该用户在此时间之前签发的 token 全部失效。 */
    public void invalidateUser(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        userNotBefore.put(username.trim(), Instant.now());
    }

    public boolean isRevoked(String jti, String username, Instant issuedAt) {
        purgeExpired();
        if (jti != null && revokedJti.containsKey(jti)) {
            return true;
        }
        if (username != null && issuedAt != null) {
            Instant nbf = userNotBefore.get(username);
            if (nbf != null && !issuedAt.isAfter(nbf)) {
                return true;
            }
        }
        return false;
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        revokedJti.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }
}
