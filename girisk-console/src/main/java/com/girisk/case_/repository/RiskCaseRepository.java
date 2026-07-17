package com.girisk.case_.repository;

import com.girisk.case_.model.RiskCase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class RiskCaseRepository {

    private static final RowMapper<RiskCase> MAPPER = (rs, i) -> new RiskCase(
            rs.getLong("id"), rs.getString("case_no"), rs.getLong("decision_log_id"),
            rs.getString("order_id"), rs.getString("user_id"),
            columnOrNull(rs, "operator_id"),
            rs.getString("status"),
            rs.getString("priority"), rs.getInt("risk_score"), rs.getString("risk_level"),
            rs.getString("assignee"), rs.getString("review_decision"), rs.getString("review_comment"),
            rs.getTimestamp("sla_deadline") != null ? rs.getTimestamp("sla_deadline").toLocalDateTime() : null,
            rs.getString("callback_status"),
            rs.getString("callback_payload"),
            rs.getTimestamp("callback_at") != null ? rs.getTimestamp("callback_at").toLocalDateTime() : null,
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime(),
            rs.getTimestamp("reviewed_at") != null ? rs.getTimestamp("reviewed_at").toLocalDateTime() : null
    );

    private static String columnOrNull(java.sql.ResultSet rs, String col) {
        try {
            return rs.getString(col);
        } catch (Exception e) {
            return null;
        }
    }

    private final JdbcTemplate jdbc;

    public RiskCaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RiskCase> findAll(String status) {
        if (status == null || status.isBlank()) {
            return jdbc.query("SELECT * FROM risk_case ORDER BY created_at DESC", MAPPER);
        }
        return jdbc.query("SELECT * FROM risk_case WHERE status = ? ORDER BY created_at DESC", MAPPER, status);
    }

    public List<RiskCase> findAll(String status, String operatorId) {
        if (operatorId == null || operatorId.isBlank()) {
            return findAll(status);
        }
        if (status == null || status.isBlank()) {
            return jdbc.query(
                    "SELECT * FROM risk_case WHERE operator_id = ? ORDER BY created_at DESC",
                    MAPPER, operatorId);
        }
        return jdbc.query(
                "SELECT * FROM risk_case WHERE status = ? AND operator_id = ? ORDER BY created_at DESC",
                MAPPER, status, operatorId);
    }

    public Optional<RiskCase> findById(long id) {
        return jdbc.query("SELECT * FROM risk_case WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public long insert(RiskCase c) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO risk_case(case_no,decision_log_id,order_id,user_id,operator_id,status,priority,risk_score,risk_level,sla_deadline,callback_status) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    new String[]{"id"});
            ps.setString(1, c.caseNo());
            ps.setLong(2, c.decisionLogId());
            ps.setString(3, c.orderId());
            ps.setString(4, c.userId());
            ps.setString(5, c.operatorId());
            ps.setString(6, c.status());
            ps.setString(7, c.priority());
            ps.setInt(8, c.riskScore());
            ps.setString(9, c.riskLevel());
            ps.setTimestamp(10, c.slaDeadline() != null ? Timestamp.valueOf(c.slaDeadline()) : null);
            ps.setString(11, c.callbackStatus() != null ? c.callbackStatus() : "NONE");
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void review(long id, String status, String decision, String comment, String assignee) {
        jdbc.update(
                "UPDATE risk_case SET status=?, review_decision=?, review_comment=?, assignee=?, reviewed_at=?, updated_at=? WHERE id=?",
                status, decision, comment, assignee, Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now()), id);
    }

    public void markCallback(long id, String callbackStatus, String payload) {
        jdbc.update(
                "UPDATE risk_case SET callback_status=?, callback_payload=?, callback_at=?, updated_at=? WHERE id=?",
                callbackStatus, payload, Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now()), id);
    }

    public long countByStatus(String status) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM risk_case WHERE status = ?", Long.class, status);
        return count != null ? count : 0;
    }
}
