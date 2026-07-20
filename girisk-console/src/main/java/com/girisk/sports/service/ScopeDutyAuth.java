package com.girisk.sports.service;

import com.girisk.common.exception.BusinessException;
import com.girisk.sports.model.LimitScopeType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 值班写权限：默认/球类仅 ADMIN；联赛/单场 ADMIN 或 REVIEWER；VIEWER 只读。
 */
@Component
public class ScopeDutyAuth {

    public String currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return "VIEWER";
        }
        for (GrantedAuthority a : auth.getAuthorities()) {
            String r = a.getAuthority();
            if (r == null) {
                continue;
            }
            if (r.startsWith("ROLE_")) {
                return r.substring(5);
            }
            return r;
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

    public boolean canWrite(LimitScopeType type) {
        String role = currentRole();
        if ("ADMIN".equals(role)) {
            return true;
        }
        if ("VIEWER".equals(role)) {
            return false;
        }
        // REVIEWER / TRADER：联赛与单场
        return type == LimitScopeType.LEAGUE || type == LimitScopeType.MATCH;
    }

    public void requireWrite(LimitScopeType type) {
        if (!canWrite(type)) {
            throw new BusinessException(
                    "无权修改 " + type.name() + " 层配置（需要 "
                            + ((type == LimitScopeType.OVERALL || type == LimitScopeType.SPORT)
                            ? "ADMIN" : "ADMIN/REVIEWER")
                            + "）");
        }
    }
}
