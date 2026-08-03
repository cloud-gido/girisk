package com.girisk.strategy.repository;

import com.girisk.strategy.model.RiskStrategy;
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
public class RiskStrategyRepository {

    private static final RowMapper<RiskStrategy> MAPPER = (rs, i) -> new RiskStrategy(
            rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("scenario"),
            rs.getString("description"), rs.getBoolean("enabled"), rs.getInt("priority"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbc;

    public RiskStrategyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RiskStrategy> findAll() {
        return jdbc.query("SELECT * FROM risk_strategy ORDER BY priority DESC, id", MAPPER);
    }

    public Optional<RiskStrategy> findById(long id) {
        return jdbc.query("SELECT * FROM risk_strategy WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<RiskStrategy> findEnabledByScenario(String scenario) {
        return jdbc.query(
                "SELECT * FROM risk_strategy WHERE scenario = ? AND enabled = TRUE ORDER BY priority DESC, id",
                MAPPER, scenario);
    }

    public long insert(RiskStrategy s) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO risk_strategy(code,name,scenario,description,enabled,priority) VALUES(?,?,?,?,?,?)",
                    new String[]{"id"});
            ps.setString(1, s.code());
            ps.setString(2, s.name());
            ps.setString(3, s.scenario());
            ps.setString(4, s.description());
            ps.setBoolean(5, s.enabled());
            ps.setInt(6, s.priority());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void update(long id, RiskStrategy s) {
        jdbc.update(
                "UPDATE risk_strategy SET name=?, scenario=?, description=?, enabled=?, priority=?, updated_at=? WHERE id=?",
                s.name(), s.scenario(), s.description(), s.enabled(), s.priority(), Timestamp.valueOf(LocalDateTime.now()), id);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM risk_strategy WHERE id = ?", id);
    }
}
