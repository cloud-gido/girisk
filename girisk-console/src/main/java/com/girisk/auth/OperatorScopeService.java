package com.girisk.auth;

import com.girisk.auth.model.SysUser;
import com.girisk.auth.repository.UserRepository;
import com.girisk.common.exception.BusinessException;
import com.girisk.sports.service.ScopeDutyAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 资源域：用户可操作的 operatorId 范围。
 * {@code *} / 空 = 不限制；否则逗号分隔白名单。
 * {@code girisk.tenant.enforce=true} 时写操作强制校验。
 */
@Service
public class OperatorScopeService {

    private final UserRepository userRepository;
    private final ScopeDutyAuth dutyAuth;
    private final boolean enforce;

    public OperatorScopeService(
            UserRepository userRepository,
            ScopeDutyAuth dutyAuth,
            @Value("${girisk.tenant.enforce:false}") boolean enforce) {
        this.userRepository = userRepository;
        this.dutyAuth = dutyAuth;
        this.enforce = enforce;
    }

    public boolean isEnforceEnabled() {
        return enforce;
    }

    public String currentActor() {
        return dutyAuth.currentUsername();
    }

    /** 写操作一律以登录用户为操作者，禁止客户端伪造。 */
    public String requireActor() {
        String u = dutyAuth.currentUsername();
        if (u == null || u.isBlank() || "anonymous".equals(u) || "system".equals(u)) {
            // system internal key 允许
            if ("system".equals(u)) {
                return "system";
            }
            throw new BusinessException("未登录，无法执行写操作");
        }
        return u;
    }

    public void assertCanAccessOperator(String operatorId) {
        if (!enforce) {
            return;
        }
        if (operatorId == null || operatorId.isBlank()) {
            throw new BusinessException("租户强制模式下必须指定 operatorId");
        }
        String username = dutyAuth.currentUsername();
        if ("system".equals(username) || dutyAuth.hasAuthority("ROLE_ADMIN")) {
            return;
        }
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (!allows(user.operatorScope(), operatorId)) {
            throw new BusinessException("无权操作租户/商户: " + operatorId);
        }
    }

    public static boolean allows(String scope, String operatorId) {
        if (scope == null || scope.isBlank() || "*".equals(scope.trim())) {
            return true;
        }
        Set<String> allowed = Arrays.stream(scope.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return allowed.contains(operatorId.trim().toLowerCase(Locale.ROOT));
    }
}
