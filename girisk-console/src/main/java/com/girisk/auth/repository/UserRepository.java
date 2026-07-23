package com.girisk.auth.repository;

import com.girisk.auth.model.SysUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private static final RowMapper<SysUser> MAPPER = (rs, i) -> new SysUser(
            rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
            rs.getString("display_name"), rs.getString("role"), rs.getBoolean("enabled"),
            rs.getTimestamp("created_at").toLocalDateTime()
    );

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
        jdbc.update(
                "INSERT INTO sys_user(username, password_hash, display_name, role) VALUES(?,?,?,?)",
                username, passwordHash, displayName, role);
        Long id = jdbc.queryForObject("SELECT id FROM sys_user WHERE username = ?", Long.class, username);
        return id != null ? id : 0L;
    }

    public void updateProfile(long id, String displayName, String primaryRole, boolean enabled) {
        jdbc.update(
                "UPDATE sys_user SET display_name = ?, role = ?, enabled = ? WHERE id = ?",
                displayName, primaryRole, enabled, id);
    }

    public void updatePassword(long id, String passwordHash) {
        jdbc.update("UPDATE sys_user SET password_hash = ? WHERE id = ?", passwordHash, id);
    }

    public void setEnabled(long id, boolean enabled) {
        jdbc.update("UPDATE sys_user SET enabled = ? WHERE id = ?", enabled, id);
    }
}
