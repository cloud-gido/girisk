package com.girisk.sports.service;

import com.girisk.auth.rbac.RbacPermissions;
import com.girisk.common.exception.BusinessException;
import com.girisk.sports.model.LimitScopeType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 值班写权限：全局/运动需 {@code duty:write_global}；联赛/单场需 match 或 global。
 */
@Component
public class ScopeDutyAuth {

    public Set<String> currentAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Set<String> out = new LinkedHashSet<>();
        if (auth == null || auth.getAuthorities() == null) {
            return out;
        }
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (a != null && a.getAuthority() != null && !a.getAuthority().isBlank()) {
                out.add(a.getAuthority());
            }
        }
        return out;
    }

    public boolean hasAuthority(String authority) {
        return currentAuthorities().contains(authority);
    }

    public String currentRole() {
        for (String a : currentAuthorities()) {
            if (a.startsWith("ROLE_")) {
                return a.substring(5);
            }
        }
        return "VIEWER";
    }

    public String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return "anonymous";
        }
        return auth.getName();
    }

    /** 仅系统管理员（ROLE_ADMIN），用于破坏性运维操作。 */
    public boolean isAdmin() {
        return hasAuthority("ROLE_" + RbacPermissions.ROLE_ADMIN);
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw new BusinessException("仅系统管理员可执行此操作");
        }
    }

    public boolean canWrite(LimitScopeType type) {
        // ROLE_ADMIN 等同全权限（兼容测试/旧 token）
        if (isAdmin() || hasAuthority(RbacPermissions.DUTY_WRITE_GLOBAL)) {
            return true;
        }
        if (type == LimitScopeType.OVERALL || type == LimitScopeType.SPORT) {
            return false;
        }
        // LEAGUE / MATCH / MATCH_PRE / MATCH_LIVE
        return hasAuthority(RbacPermissions.DUTY_WRITE_MATCH);
    }

    public void requireWrite(LimitScopeType type) {
        if (!canWrite(type)) {
            String need = (type == LimitScopeType.OVERALL || type == LimitScopeType.SPORT)
                    ? RbacPermissions.DUTY_WRITE_GLOBAL
                    : RbacPermissions.DUTY_WRITE_MATCH + " 或 " + RbacPermissions.DUTY_WRITE_GLOBAL;
            throw new BusinessException("无权修改 " + type.name() + " 层配置（需要 " + need + "）");
        }
    }

    /** 供前端/API 返回可写能力提示。 */
    public Collection<String> writeHints() {
        Set<String> hints = new LinkedHashSet<>();
        if (hasAuthority(RbacPermissions.DUTY_WRITE_GLOBAL)) {
            hints.add(RbacPermissions.DUTY_WRITE_GLOBAL);
        }
        if (hasAuthority(RbacPermissions.DUTY_WRITE_MATCH)) {
            hints.add(RbacPermissions.DUTY_WRITE_MATCH);
        }
        return hints;
    }
}
