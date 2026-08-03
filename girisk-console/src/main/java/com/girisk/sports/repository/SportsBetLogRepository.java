package com.girisk.sports.repository;

import com.girisk.sports.model.SportsBetLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SportsBetLogRepository {

    private static final RowMapper<SportsBetLog> MAPPER = (rs, i) -> new SportsBetLog(
            rs.getLong("id"),
            rs.getString("request_id"),
            rs.getString("order_id"),
            rs.getString("match_code"),
            rs.getString("market_type"),
            rs.getString("line_value"),
            rs.getString("selection"),
            rs.getBigDecimal("amount"),
            rs.getBigDecimal("odds"),
            rs.getString("decision"),
            rs.getBigDecimal("max_accept"),
            rs.getBoolean("limit_mode"),
            rs.getString("reason"),
            rs.getObject("latency_ms") != null ? rs.getInt("latency_ms") : null,
            rs.getTimestamp("created_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbc;

    public SportsBetLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(SportsBetLog log) {
        jdbc.update(
                "INSERT INTO sports_bet_log(request_id, order_id, match_code, market_type, line_value, selection, amount, odds, decision, max_accept, limit_mode, reason, latency_ms) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                log.requestId(), log.orderId(), log.matchCode(), log.marketType(), log.lineValue(),
                log.selection(), log.amount(), log.odds(), log.decision(), log.maxAccept(),
                log.limitMode(), log.reason(), log.latencyMs());
    }

    public List<SportsBetLog> findRecent(int limit) {
        return jdbc.query("SELECT * FROM sports_bet_log ORDER BY created_at DESC LIMIT ?", MAPPER, limit);
    }

    public List<SportsBetLog> findByMatch(String matchCode, int limit) {
        return jdbc.query(
                "SELECT * FROM sports_bet_log WHERE match_code = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, matchCode, limit);
    }
}
