package com.girisk.auth.rbac;

import java.util.List;

/** 稳定权限码（前后端共用）；勿在运行时新建。 */
public final class RbacPermissions {

    public static final String MONITOR_READ = "monitor:read";
    public static final String SANDBOX_USE = "sandbox:use";
    public static final String DUTY_WRITE_MATCH = "duty:write_match";
    public static final String DUTY_WRITE_GLOBAL = "duty:write_global";
    public static final String CASE_REVIEW = "case:review";
    public static final String CONFIG_MANAGE = "config:manage";
    public static final String AUDIT_READ = "audit:read";
    public static final String IAM_MANAGE = "iam:manage";

    public static final List<String> ALL = List.of(
            MONITOR_READ,
            SANDBOX_USE,
            DUTY_WRITE_MATCH,
            DUTY_WRITE_GLOBAL,
            CASE_REVIEW,
            CONFIG_MANAGE,
            AUDIT_READ,
            IAM_MANAGE);

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_REVIEWER = "REVIEWER";
    public static final String ROLE_VIEWER = "VIEWER";
    public static final String ROLE_TRADER = "TRADER";

    private RbacPermissions() {}
}
