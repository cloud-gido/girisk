package com.girisk.configcenter.repository;

import com.girisk.configcenter.model.RiskConfigRelease;
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
public class RiskConfigReleaseRepository {

    private static final RowMapper<RiskConfigRelease> MAPPER = (rs, i) -> new RiskConfigRelease(
            rs.getLong("id"),
            rs.getLong("config_epoch"),
            rs.getString("scope"),
            rs.getString("status"),
            rs.getString("param_set_version"),
            rs.getString("rule_set_version"),
            rs.getString("param_set_json"),
            rs.getString("rule_set_json"),
            rs.getString("change_summary"),
            rs.getString("created_by"),
            rs.getString("submitted_by"),
            rs.getString("approved_by"),
            rs.getString("published_by"),
            rs.getString("approval_ticket"),
            rs.getString("reject_reason"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            ts(rs, "submitted_at"),
            ts(rs, "approved_at"),
            ts(rs, "published_at")
    );

    private static LocalDateTime ts(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t != null ? t.toLocalDateTime() : null;
    }

    private final JdbcTemplate jdbc;

    public RiskConfigReleaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RiskConfigRelease> findAll() {
        return jdbc.query("SELECT * FROM risk_config_release ORDER BY config_epoch DESC", MAPPER);
    }

    public Optional<RiskConfigRelease> findById(long id) {
        return jdbc.query("SELECT * FROM risk_config_release WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<RiskConfigRelease> findPublished() {
        return jdbc.query(
                "SELECT * FROM risk_config_release WHERE status = 'PUBLISHED' ORDER BY config_epoch DESC LIMIT 1",
                MAPPER).stream().findFirst();
    }

    public long nextEpoch() {
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(config_epoch), 0) FROM risk_config_release", Long.class);
        return (max != null ? max : 0L) + 1;
    }

    public long insert(RiskConfigRelease r) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                    INSERT INTO risk_config_release(
                      config_epoch,scope,status,param_set_version,rule_set_version,
                      param_set_json,rule_set_json,change_summary,created_by
                    ) VALUES(?,?,?,?,?,?,?,?,?)
                    """,
                    new String[]{"id"});
            ps.setLong(1, r.configEpoch());
            ps.setString(2, r.scope());
            ps.setString(3, r.status());
            ps.setString(4, r.paramSetVersion());
            ps.setString(5, r.ruleSetVersion());
            ps.setString(6, r.paramSetJson());
            ps.setString(7, r.ruleSetJson());
            ps.setString(8, r.changeSummary());
            ps.setString(9, r.createdBy());
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void markSubmitted(long id, String actor) {
        jdbc.update("UPDATE risk_config_release SET status='PENDING_APPROVAL', submitted_by=?, submitted_at=? WHERE id=?",
                actor, Timestamp.valueOf(LocalDateTime.now()), id);
    }

    public void markApproved(long id, String actor, String ticket) {
        jdbc.update("UPDATE risk_config_release SET status='APPROVED', approved_by=?, approved_at=?, approval_ticket=? WHERE id=?",
                actor, Timestamp.valueOf(LocalDateTime.now()), ticket, id);
    }

    public void markRejected(long id, String actor, String reason) {
        jdbc.update("UPDATE risk_config_release SET status='REJECTED', approved_by=?, approved_at=?, reject_reason=? WHERE id=?",
                actor, Timestamp.valueOf(LocalDateTime.now()), reason, id);
    }

    public void markPublished(long id, String actor) {
        jdbc.update("UPDATE risk_config_release SET status='PUBLISHED', published_by=?, published_at=? WHERE id=?",
                actor, Timestamp.valueOf(LocalDateTime.now()), id);
    }
}
