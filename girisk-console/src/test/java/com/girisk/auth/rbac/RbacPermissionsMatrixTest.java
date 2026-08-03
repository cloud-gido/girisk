package com.girisk.auth.rbac;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RbacPermissionsMatrixTest {

    @Test
    void adminHasAll() {
        RbacService svc = new RbacService(null);
        List<String> perms = svc.defaultPermissionsForRole("ADMIN");
        assertTrue(perms.containsAll(RbacPermissions.ALL));
    }

    @Test
    void viewerIsReadOnly() {
        RbacService svc = new RbacService(null);
        List<String> perms = svc.defaultPermissionsForRole("VIEWER");
        assertTrue(perms.contains(RbacPermissions.MONITOR_READ));
        assertTrue(perms.contains(RbacPermissions.AUDIT_READ));
        assertTrue(!perms.contains(RbacPermissions.IAM_MANAGE));
        assertTrue(!perms.contains(RbacPermissions.CONFIG_MANAGE));
    }

    @Test
    void traderHasMatchDutyNotGlobal() {
        RbacService svc = new RbacService(null);
        List<String> perms = svc.defaultPermissionsForRole("TRADER");
        assertTrue(perms.contains(RbacPermissions.DUTY_WRITE_MATCH));
        assertTrue(!perms.contains(RbacPermissions.DUTY_WRITE_GLOBAL));
    }
}
