package com.girisk.auth.repository;

import com.girisk.auth.model.SysUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private static final RowMapper<SysUser> MAPPER = (rs, i) -> {
        String scope = "*";
        try {
            String s = rs.getString("operator_scope");
            if (s != null && !s.isBlank()) {
                scope = s;
            }
        } catch (Exception ignored) {
            // 旧库尚未加列时容错
        }
        return new SysUser(
                rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("display_name"), rs.getString("role"), rs.getBoolean("enabled"),
                scope,
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    };

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SysUser> findByUsername(String username) {
        return jdbc.query("SELECT * FROM sys_user WHERE username = ?", MAPPER, username).stream().findFirst();
    }

    public Optional<SysUser> findById(long id) {
        return jdbc.query("SELECT * FROM sys_user WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<SysUser> listAll() {
        return jdbc.query("SELECT * FROM sys_user ORDER BY id", MAPPER);
    }

    public long count() {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class);
        return c != null ? c : 0;
    }

    public long insert(String username, String passwordHash, String displayName, String role) {
        return insert(username, passwordHash, displayName, role, "*");
    }

    public long insert(String username, String passwordHash, String displayName, String role, String operatorScope) {
        jdbc.update(
                "INSERT INTO sys_user(username, password_hash, display_name, role, operator_scope) VALUES(?,?,?,?,?)",
                username, passwordHash, displayName, role, operatorScope == null || operatorScope.isBlank() ? "*" : operatorScope);
        Long id = jdbc.queryForObject("SELECT id FROM sys_user WHERE username = ?", Long.class, username);
        return id != null ? id : 0L;
    }

    public void updateProfile(long id, String displayName, String primaryRole, boolean enabled, String operatorScope) {
        jdbc.update(
                "UPDATE sys_user SET display_name = ?, role = ?, enabled = ?, operator_scope = ? WHERE id = ?",
                displayName, primaryRole, enabled,
                operatorScope == null || operatorScope.isBlank() ? "*" : operatorScope,
                id);
    }

    public void updatePassword(long id, String passwordHash) {
        jdbc.update("UPDATE sys_user SET password_hash = ? WHERE id = ?", passwordHash, id);
    }

    public void setEnabled(long id, boolean enabled) {
        jdbc.update("UPDATE sys_user SET enabled = ? WHERE id = ?", enabled, id);
    }
}
