package com.girisk.auth.rbac;

import com.girisk.auth.model.SysUser;
import com.girisk.auth.repository.RbacRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RbacService {

    private final RbacRepository rbacRepository;

    public RbacService(RbacRepository rbacRepository) {
        this.rbacRepository = rbacRepository;
    }

    public List<String> rolesForUser(SysUser user) {
        List<String> codes = rbacRepository.findRoleCodesByUserId(user.id());
        if (!codes.isEmpty()) {
            return codes;
        }
        if (user.role() != null && !user.role().isBlank()) {
            return List.of(user.role().trim().toUpperCase());
        }
        return List.of(RbacPermissions.ROLE_VIEWER);
    }

    public List<String> permissionsForUser(SysUser user) {
        List<String> perms = rbacRepository.findPermissionCodesByUserId(user.id());
        if (!perms.isEmpty()) {
            return perms;
        }
        // 兼容：关联表未迁移时按主角色推权限
        return defaultPermissionsForRole(user.role());
    }

    public List<String> defaultPermissionsForRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return List.of(RbacPermissions.MONITOR_READ);
        }
        return switch (roleCode.trim().toUpperCase()) {
            case RbacPermissions.ROLE_ADMIN -> new ArrayList<>(RbacPermissions.ALL);
            case RbacPermissions.ROLE_REVIEWER -> List.of(
                    RbacPermissions.MONITOR_READ,
                    RbacPermissions.SANDBOX_USE,
                    RbacPermissions.DUTY_WRITE_MATCH,
                    RbacPermissions.CASE_REVIEW,
                    RbacPermissions.AUDIT_READ);
            case RbacPermissions.ROLE_TRADER -> List.of(
                    RbacPermissions.MONITOR_READ,
                    RbacPermissions.SANDBOX_USE,
                    RbacPermissions.DUTY_WRITE_MATCH);
            default -> List.of(RbacPermissions.MONITOR_READ, RbacPermissions.AUDIT_READ);
        };
    }

    /** 内部 API Key：等价 ADMIN 全权限。 */
    public List<String> systemAuthorities() {
        Set<String> authorities = new LinkedHashSet<>();
        authorities.add("ROLE_" + RbacPermissions.ROLE_ADMIN);
        authorities.addAll(RbacPermissions.ALL);
        return List.copyOf(authorities);
    }

    public void bindUserToPrimaryRole(long userId, String roleCode) {
        rbacRepository.findRoleByCode(roleCode).ifPresent(role ->
                rbacRepository.replaceUserRoles(userId, List.of(role.id())));
    }
}
