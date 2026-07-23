package com.girisk.auth;

import com.girisk.audit.OpsAuditService;
import com.girisk.auth.dto.LoginRequest;
import com.girisk.auth.dto.LoginResponse;
import com.girisk.auth.dto.UserProfile;
import com.girisk.auth.model.SysUser;
import com.girisk.auth.rbac.RbacService;
import com.girisk.auth.repository.UserRepository;
import com.girisk.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RbacService rbacService;
    private final TokenRevocationStore revocationStore;
    private final OpsAuditService opsAudit;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RbacService rbacService,
            TokenRevocationStore revocationStore,
            OpsAuditService opsAudit) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.rbacService = rbacService;
        this.revocationStore = revocationStore;
        this.opsAudit = opsAudit;
    }

    public LoginResponse login(LoginRequest request) {
        SysUser user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));
        if (!user.enabled() || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new BusinessException("用户名或密码错误");
        }
        List<String> roles = rbacService.rolesForUser(user);
        List<String> permissions = rbacService.permissionsForUser(user);
        String primaryRole = roles.isEmpty() ? user.role() : roles.get(0);
        String token = jwtTokenProvider.createToken(user, roles, permissions);
        opsAudit.record(OpsAuditService.AUTH_LOGIN, "用户登录", "user=" + user.username());
        return new LoginResponse(
                token, user.username(), user.displayName(), primaryRole, roles, permissions,
                user.operatorScope());
    }

    public UserProfile profile(String username) {
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        List<String> roles = rbacService.rolesForUser(user);
        List<String> permissions = rbacService.permissionsForUser(user);
        String primaryRole = roles.isEmpty() ? user.role() : roles.get(0);
        return new UserProfile(
                user.username(), user.displayName(), primaryRole, roles, permissions, user.operatorScope());
    }

    public void logout(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return;
        }
        String raw = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7) : bearerToken;
        try {
            Claims claims = jwtTokenProvider.parse(raw);
            String jti = claims.getId();
            Instant exp = claims.getExpiration() != null ? claims.getExpiration().toInstant() : Instant.now().plusSeconds(3600);
            revocationStore.revokeJti(jti, exp);
            opsAudit.record(OpsAuditService.AUTH_LOGOUT, "用户登出", "user=" + claims.getSubject());
        } catch (Exception ignored) {
            // token 已无效则静默
        }
    }
}
