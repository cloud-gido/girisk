package com.girisk.auth.repository;

import com.girisk.auth.model.SysUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

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

    public long count() {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class);
        return c != null ? c : 0;
    }

    public void insert(String username, String passwordHash, String displayName, String role) {
        jdbc.update(
                "INSERT INTO sys_user(username, password_hash, display_name, role) VALUES(?,?,?,?)",
                username, passwordHash, displayName, role);
    }
}
