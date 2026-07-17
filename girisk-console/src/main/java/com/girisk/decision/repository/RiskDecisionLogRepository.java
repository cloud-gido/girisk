package com.girisk.decision.repository;

import com.girisk.decision.model.RiskDecisionLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class RiskDecisionLogRepository {

    private static final RowMapper<RiskDecisionLog> MAPPER = (rs, i) -> new RiskDecisionLog(
            rs.getLong("id"),
            rs.getString("request_id"),
            rs.getString("order_id"),
            rs.getString("user_id"),
            rs.getString("scenario"),
            rs.getString("strategy_code"),
            rs.getString("decision"),
            rs.getInt("risk_score"),
            rs.getString("risk_level"),
            rs.getString("hit_rules"),
            rs.getString("reason"),
            rs.getBigDecimal("amount"),
            rs.getString("ip"),
            rs.getString("device_id"),
            rs.getObject("latency_ms") != null ? rs.getInt("latency_ms") : null,
            rs.getString("source"),
            rs.getString("trace_id"),
            rs.getString("fixture_id"),
            rs.getString("operator_id"),
            rs.getString("market_json"),
            rs.getObject("stake_cents") != null ? rs.getLong("stake_cents") : null,
            rs.getString("odds"),
            rs.getObject("payout_cents") != null ? rs.getLong("payout_cents") : null,
            rs.getObject("max_acceptable_stake_cents") != null ? rs.getLong("max_acceptable_stake_cents") : null,
            rs.getString("reasons_json"),
            rs.getString("versions_json"),
            rs.getString("feature_snapshot_json"),
            rs.getTimestamp("created_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbc;

    public RiskDecisionLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RiskDecisionLog> findRecent(int limit) {
        return jdbc.query("SELECT * FROM risk_decision_log ORDER BY created_at DESC LIMIT ?", MAPPER, limit);
    }

    public List<RiskDecisionLog> findRecentByOperator(String operatorId, int limit) {
        if (operatorId == null || operatorId.isBlank()) {
            return findRecent(limit);
        }
        return jdbc.query(
                "SELECT * FROM risk_decision_log WHERE operator_id = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, operatorId, limit);
    }

    public Optional<RiskDecisionLog> findById(long id) {
        return jdbc.query("SELECT * FROM risk_decision_log WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<RiskDecisionLog> findByOrderId(String orderId) {
        return jdbc.query(
                "SELECT * FROM risk_decision_log WHERE order_id = ? ORDER BY created_at DESC",
                MAPPER, orderId);
    }

    public Optional<RiskDecisionLog> findByTraceId(String traceId) {
        return jdbc.query(
                "SELECT * FROM risk_decision_log WHERE trace_id = ? ORDER BY created_at DESC LIMIT 1",
                MAPPER, traceId).stream().findFirst();
    }

    public long insert(RiskDecisionLog log) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                    INSERT INTO risk_decision_log(
                      request_id,order_id,user_id,scenario,strategy_code,decision,risk_score,risk_level,
                      hit_rules,reason,amount,ip,device_id,latency_ms,source,
                      trace_id,fixture_id,operator_id,market_json,stake_cents,odds,payout_cents,
                      max_acceptable_stake_cents,reasons_json,versions_json,feature_snapshot_json
                    ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    new String[]{"id"});
            int i = 1;
            ps.setString(i++, log.requestId());
            ps.setString(i++, log.orderId());
            ps.setString(i++, log.userId());
            ps.setString(i++, log.scenario());
            ps.setString(i++, log.strategyCode());
            ps.setString(i++, log.decision());
            ps.setInt(i++, log.riskScore());
            ps.setString(i++, log.riskLevel());
            ps.setString(i++, log.hitRules());
            ps.setString(i++, log.reason());
            ps.setBigDecimal(i++, log.amount());
            ps.setString(i++, log.ip());
            ps.setString(i++, log.deviceId());
            if (log.latencyMs() != null) ps.setInt(i++, log.latencyMs());
            else ps.setObject(i++, null);
            ps.setString(i++, log.source());
            ps.setString(i++, log.traceId());
            ps.setString(i++, log.fixtureId());
            ps.setString(i++, log.operatorId());
            ps.setString(i++, log.marketJson());
            if (log.stakeCents() != null) ps.setLong(i++, log.stakeCents());
            else ps.setObject(i++, null);
            ps.setString(i++, log.odds());
            if (log.payoutCents() != null) ps.setLong(i++, log.payoutCents());
            else ps.setObject(i++, null);
            if (log.maxAcceptableStakeCents() != null) ps.setLong(i++, log.maxAcceptableStakeCents());
            else ps.setObject(i++, null);
            ps.setString(i++, log.reasonsJson());
            ps.setString(i++, log.versionsJson());
            ps.setString(i, log.featureSnapshotJson());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}
