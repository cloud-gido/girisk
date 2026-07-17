package com.girisk.rule.repository;

import com.girisk.rule.model.RiskRule;
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
public class RiskRuleRepository {

    private static final RowMapper<RiskRule> MAPPER = (rs, i) -> new RiskRule(
            rs.getLong("id"), rs.getLong("strategy_id"), rs.getString("code"), rs.getString("name"),
            rs.getString("rule_type"), rs.getString("field"), rs.getString("operator"),
            rs.getString("threshold"), rs.getString("action"), rs.getInt("score_weight"),
            rs.getInt("priority"), rs.getBoolean("enabled"), rs.getString("description"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbc;

    public RiskRuleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RiskRule> findAll() {
        return jdbc.query("SELECT * FROM risk_rule ORDER BY priority, id", MAPPER);
    }

    public List<RiskRule> findByStrategyId(long strategyId) {
        return jdbc.query("SELECT * FROM risk_rule WHERE strategy_id = ? ORDER BY priority, id", MAPPER, strategyId);
    }

    public Optional<RiskRule> findById(long id) {
        return jdbc.query("SELECT * FROM risk_rule WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public long insert(RiskRule r) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO risk_rule(strategy_id,code,name,rule_type,field,operator,threshold,action,score_weight,priority,enabled,description) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    new String[]{"id"});
            ps.setLong(1, r.strategyId());
            ps.setString(2, r.code());
            ps.setString(3, r.name());
            ps.setString(4, r.ruleType());
            ps.setString(5, r.field());
            ps.setString(6, r.operator());
            ps.setString(7, r.threshold());
            ps.setString(8, r.action());
            ps.setInt(9, r.scoreWeight());
            ps.setInt(10, r.priority());
            ps.setBoolean(11, r.enabled());
            ps.setString(12, r.description());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void update(long id, RiskRule r) {
        jdbc.update(
                "UPDATE risk_rule SET strategy_id=?, code=?, name=?, rule_type=?, field=?, operator=?, threshold=?, action=?, score_weight=?, priority=?, enabled=?, description=?, updated_at=? WHERE id=?",
                r.strategyId(), r.code(), r.name(), r.ruleType(), r.field(), r.operator(), r.threshold(),
                r.action(), r.scoreWeight(), r.priority(), r.enabled(), r.description(),
                Timestamp.valueOf(LocalDateTime.now()), id);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM risk_rule WHERE id = ?", id);
    }
}
