package com.girisk.event.repository;

import com.girisk.event.model.RiskEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RiskEventRepository {

    private static final RowMapper<RiskEvent> MAPPER = (rs, i) -> new RiskEvent(
            rs.getLong("id"), rs.getString("event_type"), rs.getString("severity"),
            rs.getString("order_id"), rs.getString("user_id"), rs.getString("title"),
            rs.getString("detail"), rs.getTimestamp("created_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbc;

    public RiskEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RiskEvent> findRecent(int limit) {
        return jdbc.query("SELECT * FROM risk_event ORDER BY created_at DESC LIMIT ?", MAPPER, limit);
    }

    public void insert(String eventType, String severity, String orderId, String userId, String title, String detail) {
        jdbc.update(
                "INSERT INTO risk_event(event_type,severity,order_id,user_id,title,detail) VALUES(?,?,?,?,?,?)",
                eventType, severity, orderId, userId, title, detail);
    }
}
