package com.girisk.config;

import com.girisk.auth.rbac.RbacPermissions;
import com.girisk.auth.rbac.RbacService;
import com.girisk.auth.repository.RbacRepository;
import com.girisk.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class UserDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UserDataInitializer.class);

    private final UserRepository userRepository;
    private final RbacRepository rbacRepository;
    private final RbacService rbacService;
    private final PasswordEncoder passwordEncoder;

    public UserDataInitializer(
            UserRepository userRepository,
            RbacRepository rbacRepository,
            RbacService rbacService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.rbacRepository = rbacRepository;
        this.rbacService = rbacService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedPermissions();
        seedRoles();
        seedRolePermissions();
        seedUsersIfEmpty();
        migrateUserRoles();
        log.info("RBAC seed ready: roles={}, permissions={}",
                rbacRepository.listRoles().size(),
                rbacRepository.listPermissions().size());
    }

    private void seedPermissions() {
        Map<String, String[]> defs = Map.of(
                RbacPermissions.MONITOR_READ, new String[]{"监控只读", "monitor", "敞口/决策/回放只读"},
                RbacPermissions.SANDBOX_USE, new String[]{"调试沙箱", "sandbox", "试算/管线/接口实验室"},
                RbacPermissions.DUTY_WRITE_MATCH, new String[]{"赛事值班写", "duty", "联赛/赛事限额与闸门"},
                RbacPermissions.DUTY_WRITE_GLOBAL, new String[]{"全局值班写", "duty", "全局/运动级限额与闸门"},
                RbacPermissions.CASE_REVIEW, new String[]{"工单审核", "case", "审核工单处理"},
                RbacPermissions.CONFIG_MANAGE, new String[]{"策略配置", "config", "规则/策略/名单/配置发布"},
                RbacPermissions.AUDIT_READ, new String[]{"审计只读", "audit", "审计与事件查询"},
                RbacPermissions.IAM_MANAGE, new String[]{"账号管理", "iam", "用户与角色管理"});
        for (var e : defs.entrySet()) {
            if (rbacRepository.findPermissionByCode(e.getKey()).isEmpty()) {
                String[] v = e.getValue();
                rbacRepository.insertPermission(e.getKey(), v[0], v[1], v[2]);
            }
        }
    }

    private void seedRoles() {
        ensureRole(RbacPermissions.ROLE_ADMIN, "管理员", "全部权限");
        ensureRole(RbacPermissions.ROLE_REVIEWER, "审核员", "监控/沙箱/赛事值班/工单/审计");
        ensureRole(RbacPermissions.ROLE_VIEWER, "观察员", "监控与审计只读");
        ensureRole(RbacPermissions.ROLE_TRADER, "交易员", "监控/沙箱/赛事值班");
    }

    private void ensureRole(String code, String name, String description) {
        if (rbacRepository.findRoleByCode(code).isEmpty()) {
            rbacRepository.insertRole(code, name, true, description);
        }
    }

    private void seedRolePermissions() {
        bindRolePerms(RbacPermissions.ROLE_ADMIN, RbacPermissions.ALL);
        bindRolePerms(RbacPermissions.ROLE_REVIEWER, List.of(
                RbacPermissions.MONITOR_READ,
                RbacPermissions.SANDBOX_USE,
                RbacPermissions.DUTY_WRITE_MATCH,
                RbacPermissions.CASE_REVIEW,
                RbacPermissions.AUDIT_READ));
        bindRolePerms(RbacPermissions.ROLE_VIEWER, List.of(
                RbacPermissions.MONITOR_READ,
                RbacPermissions.AUDIT_READ));
        bindRolePerms(RbacPermissions.ROLE_TRADER, List.of(
                RbacPermissions.MONITOR_READ,
                RbacPermissions.SANDBOX_USE,
                RbacPermissions.DUTY_WRITE_MATCH));
    }

    private void bindRolePerms(String roleCode, List<String> permCodes) {
        var role = rbacRepository.findRoleByCode(roleCode).orElse(null);
        if (role == null) {
            return;
        }
        for (String code : permCodes) {
            rbacRepository.findPermissionByCode(code).ifPresent(p ->
                    rbacRepository.ensureRolePermission(role.id(), p.id()));
        }
    }

    private void seedUsersIfEmpty() {
        if (userRepository.count() > 0) {
            return;
        }
        createUser("admin", "admin123", "系统管理员", RbacPermissions.ROLE_ADMIN);
        createUser("reviewer", "review123", "审核员", RbacPermissions.ROLE_REVIEWER);
        createUser("viewer", "view123", "观察员", RbacPermissions.ROLE_VIEWER);
        createUser("trader", "trade123", "交易员", RbacPermissions.ROLE_TRADER);
    }

    private void createUser(String username, String password, String displayName, String role) {
        long id = userRepository.insert(username, passwordEncoder.encode(password), displayName, role);
        rbacService.bindUserToPrimaryRole(id, role);
    }

    private void migrateUserRoles() {
        for (var user : userRepository.listAll()) {
            if (!rbacRepository.findRoleIdsByUserId(user.id()).isEmpty()) {
                continue;
            }
            String role = user.role() == null ? RbacPermissions.ROLE_VIEWER : user.role().trim().toUpperCase();
            rbacService.bindUserToPrimaryRole(user.id(), role);
        }
    }
}
