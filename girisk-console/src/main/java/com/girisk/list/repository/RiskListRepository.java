package com.girisk.list.repository;

import com.girisk.list.model.RiskListEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class RiskListRepository {

    private static final RowMapper<RiskListEntry> MAPPER = (rs, i) -> new RiskListEntry(
            rs.getLong("id"), rs.getString("list_type"), rs.getString("list_key"),
            rs.getString("list_value"), rs.getString("reason"), rs.getString("source"),
            rs.getTimestamp("expires_at") != null ? rs.getTimestamp("expires_at").toLocalDateTime() : null,
            rs.getBoolean("enabled"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbc;

    public RiskListRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RiskListEntry> findAll(String listType) {
        if (listType == null || listType.isBlank()) {
            return jdbc.query("SELECT * FROM risk_list_entry ORDER BY id DESC", MAPPER);
        }
        return jdbc.query("SELECT * FROM risk_list_entry WHERE list_type = ? ORDER BY id DESC", MAPPER, listType);
    }

    public boolean exists(String listType, String listKey, String listValue) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM risk_list_entry WHERE list_type = ? AND list_key = ? AND list_value = ? AND enabled = TRUE AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)",
                Integer.class, listType, listKey, listValue);
        return count != null && count > 0;
    }

    public long insert(RiskListEntry e) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO risk_list_entry(list_type,list_key,list_value,reason,source,expires_at,enabled) VALUES(?,?,?,?,?,?,?)",
                    new String[]{"id"});
            ps.setString(1, e.listType());
            ps.setString(2, e.listKey());
            ps.setString(3, e.listValue());
            ps.setString(4, e.reason());
            ps.setString(5, e.source());
            ps.setTimestamp(6, e.expiresAt() != null ? Timestamp.valueOf(e.expiresAt()) : null);
            ps.setBoolean(7, e.enabled());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM risk_list_entry WHERE id = ?", id);
    }

    public void toggle(long id, boolean enabled) {
        jdbc.update("UPDATE risk_list_entry SET enabled = ?, updated_at = ? WHERE id = ?",
                enabled, Timestamp.valueOf(LocalDateTime.now()), id);
    }
}
