package com.girisk.dashboard;

import com.girisk.configcenter.repository.RiskFixtureViewRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final JdbcTemplate jdbc;
    private final RiskFixtureViewRepository fixtureViewRepository;

    public DashboardService(JdbcTemplate jdbc, RiskFixtureViewRepository fixtureViewRepository) {
        this.jdbc = jdbc;
        this.fixtureViewRepository = fixtureViewRepository;
    }

    public Map<String, Object> overview() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDecisions", count("SELECT COUNT(*) FROM risk_decision_log"));
        stats.put("passCount", count("SELECT COUNT(*) FROM risk_decision_log WHERE decision = 'PASS'"));
        stats.put("rejectCount", count("SELECT COUNT(*) FROM risk_decision_log WHERE decision = 'REJECT'"));
        stats.put("reviewCount", count("SELECT COUNT(*) FROM risk_decision_log WHERE decision = 'REVIEW'"));
        stats.put("limitCount", count("SELECT COUNT(*) FROM risk_decision_log WHERE decision = 'LIMIT'"));
        stats.put("pendingCases", count("SELECT COUNT(*) FROM risk_case WHERE status = 'PENDING'"));
        stats.put("activeRules", count("SELECT COUNT(*) FROM risk_rule WHERE enabled = TRUE"));
        stats.put("listEntries", count("SELECT COUNT(*) FROM risk_list_entry WHERE enabled = TRUE"));
        stats.put("publishedConfigEpoch", publishedEpoch());
        stats.put("highRiskFixtures", count("SELECT COUNT(*) FROM risk_fixture_view WHERE risk_level IN ('HIGH','CRITICAL')"));
        stats.put("avgLatencyMs", avgLatency());
        stats.put("decisionTrend", decisionTrend());
        stats.put("riskDistribution", riskDistribution());
        stats.put("topFixtures", fixtureViewRepository.findTopByWorstLoss(5));
        stats.put("recentRejectReasons", recentRejectReasons());
        return stats;
    }

    private long publishedEpoch() {
        Long v = jdbc.queryForObject(
                "SELECT COALESCE(MAX(config_epoch), 0) FROM risk_config_release WHERE status = 'PUBLISHED'",
                Long.class);
        return v != null ? v : 0;
    }

    private List<Map<String, Object>> recentRejectReasons() {
        return jdbc.queryForList(
                """
                SELECT decision, reason, COUNT(*) AS cnt
                FROM risk_decision_log
                WHERE decision IN ('REJECT','LIMIT','REVIEW')
                GROUP BY decision, reason
                ORDER BY cnt DESC
                LIMIT 8
                """);
    }

    private long count(String sql) {
        Long v = jdbc.queryForObject(sql, Long.class);
        return v != null ? v : 0;
    }

    private double avgLatency() {
        Double v = jdbc.queryForObject(
                "SELECT AVG(latency_ms) FROM risk_decision_log WHERE latency_ms IS NOT NULL", Double.class);
        return v != null ? Math.round(v * 10) / 10.0 : 0;
    }

    private List<Map<String, Object>> decisionTrend() {
        return jdbc.queryForList(
                "SELECT decision, COUNT(*) AS cnt FROM risk_decision_log GROUP BY decision");
    }

    private List<Map<String, Object>> riskDistribution() {
        return jdbc.queryForList(
                "SELECT risk_level AS level, COUNT(*) AS cnt FROM risk_decision_log GROUP BY risk_level");
    }
}
