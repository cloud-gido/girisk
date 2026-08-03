package com.girisk.configcenter.repository;

import com.girisk.configcenter.model.RiskFixtureView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RiskFixtureViewRepository {

    private static final RowMapper<RiskFixtureView> MAPPER = (rs, i) -> new RiskFixtureView(
            rs.getLong("id"),
            rs.getString("fixture_id"),
            rs.getString("home_team"),
            rs.getString("away_team"),
            rs.getString("operator_id"),
            rs.getInt("confirmed_orders"),
            rs.getInt("pending_reserved"),
            rs.getLong("worst_loss_cents"),
            rs.getString("worst_score"),
            rs.getString("live_score"),
            rs.getString("market_summary_json"),
            rs.getString("risk_level"),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbc;

    public RiskFixtureViewRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RiskFixtureView> findTopByWorstLoss(int limit) {
        return jdbc.query(
                "SELECT * FROM risk_fixture_view ORDER BY worst_loss_cents DESC LIMIT ?",
                MAPPER, limit);
    }

    public List<RiskFixtureView> findAll() {
        return jdbc.query("SELECT * FROM risk_fixture_view ORDER BY worst_loss_cents DESC", MAPPER);
    }
}
