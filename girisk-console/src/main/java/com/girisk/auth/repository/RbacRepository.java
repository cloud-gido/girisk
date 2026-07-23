package com.girisk.auth.repository;

import com.girisk.auth.model.SysPermission;
import com.girisk.auth.model.SysRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RbacRepository {

    private static final RowMapper<SysRole> ROLE_MAPPER = (rs, i) -> new SysRole(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getBoolean("builtin"),
            rs.getString("description"),
            rs.getTimestamp("created_at").toLocalDateTime());

    private static final RowMapper<SysPermission> PERM_MAPPER = (rs, i) -> new SysPermission(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("module"),
            rs.getString("description"),
            rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public RbacRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SysRole> findRoleByCode(String code) {
        return jdbc.query("SELECT * FROM sys_role WHERE code = ?", ROLE_MAPPER, code).stream().findFirst();
    }

    public Optional<SysRole> findRoleById(long id) {
        return jdbc.query("SELECT * FROM sys_role WHERE id = ?", ROLE_MAPPER, id).stream().findFirst();
    }

    public List<SysRole> listRoles() {
        return jdbc.query("SELECT * FROM sys_role ORDER BY code", ROLE_MAPPER);
    }

    public void insertRole(String code, String name, boolean builtin, String description) {
        jdbc.update(
                "INSERT INTO sys_role(code, name, builtin, description) VALUES(?,?,?,?)",
                code, name, builtin, description);
    }

    public Optional<SysPermission> findPermissionByCode(String code) {
        return jdbc.query("SELECT * FROM sys_permission WHERE code = ?", PERM_MAPPER, code).stream().findFirst();
    }

    public List<SysPermission> listPermissions() {
        return jdbc.query("SELECT * FROM sys_permission ORDER BY module, code", PERM_MAPPER);
    }

    public void insertPermission(String code, String name, String module, String description) {
        jdbc.update(
                "INSERT INTO sys_permission(code, name, module, description) VALUES(?,?,?,?)",
                code, name, module, description);
    }

    public void ensureRolePermission(long roleId, long permissionId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_permission WHERE role_id = ? AND permission_id = ?",
                Integer.class, roleId, permissionId);
        if (n != null && n > 0) {
            return;
        }
        jdbc.update(
                "INSERT INTO sys_role_permission(role_id, permission_id) VALUES(?,?)",
                roleId, permissionId);
    }

    public void replaceRolePermissions(long roleId, List<Long> permissionIds) {
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id = ?", roleId);
        for (Long pid : permissionIds) {
            if (pid != null) {
                jdbc.update(
                        "INSERT INTO sys_role_permission(role_id, permission_id) VALUES(?,?)",
                        roleId, pid);
            }
        }
    }

    public void ensureUserRole(long userId, long roleId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role WHERE user_id = ? AND role_id = ?",
                Integer.class, userId, roleId);
        if (n != null && n > 0) {
            return;
        }
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES(?,?)", userId, roleId);
    }

    public void replaceUserRoles(long userId, List<Long> roleIds) {
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
        for (Long rid : roleIds) {
            if (rid != null) {
                jdbc.update("INSERT INTO sys_user_role(user_id, role_id) VALUES(?,?)", userId, rid);
            }
        }
    }

    public List<String> findRoleCodesByUserId(long userId) {
        return jdbc.queryForList(
                """
                SELECT r.code FROM sys_role r
                INNER JOIN sys_user_role ur ON ur.role_id = r.id
                WHERE ur.user_id = ?
                ORDER BY r.code
                """,
                String.class,
                userId);
    }

    public List<String> findPermissionCodesByUserId(long userId) {
        return jdbc.queryForList(
                """
                SELECT DISTINCT p.code FROM sys_permission p
                INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
                INNER JOIN sys_user_role ur ON ur.role_id = rp.role_id
                WHERE ur.user_id = ?
                ORDER BY p.code
                """,
                String.class,
                userId);
    }

    public List<String> findPermissionCodesByRoleId(long roleId) {
        return jdbc.queryForList(
                """
                SELECT p.code FROM sys_permission p
                INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
                WHERE rp.role_id = ?
                ORDER BY p.code
                """,
                String.class,
                roleId);
    }

    public List<Long> findPermissionIdsByRoleId(long roleId) {
        return jdbc.queryForList(
                "SELECT permission_id FROM sys_role_permission WHERE role_id = ?",
                Long.class,
                roleId);
    }

    public List<Long> findRoleIdsByUserId(long userId) {
        return jdbc.queryForList(
                "SELECT role_id FROM sys_user_role WHERE user_id = ?",
                Long.class,
                userId);
    }
}
