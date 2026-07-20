package com.girisk.sports.repository;

import com.girisk.sports.model.SportsMatch;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class SportsMatchRepository {

    private static final RowMapper<SportsMatch> MAPPER = (rs, i) -> new SportsMatch(
            rs.getLong("id"),
            rs.getString("match_code"),
            rs.getString("home_team"),
            rs.getString("away_team"),
            columnOr(rs, "sport_code", "football"),
            columnOr(rs, "league_code", "UNKNOWN"),
            columnOr(rs, "league_name", "未分组联赛"),
            rs.getBigDecimal("exposure_threshold"),
            rs.getBoolean("limit_mode"),
            rs.getBigDecimal("current_exposure"),
            rs.getBigDecimal("delta"),
            rs.getString("status"),
            rs.getTimestamp("last_check_at") != null ? rs.getTimestamp("last_check_at").toLocalDateTime() : null,
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private static String columnOr(java.sql.ResultSet rs, String col, String fallback) {
        try {
            String v = rs.getString(col);
            return v == null || v.isBlank() ? fallback : v;
        } catch (Exception e) {
            return fallback;
        }
    }

    private final JdbcTemplate jdbc;

    public SportsMatchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SportsMatch> findAll() {
        return jdbc.query("SELECT * FROM sports_match ORDER BY id", MAPPER);
    }

    public List<SportsMatch> findActive() {
        return jdbc.query("SELECT * FROM sports_match WHERE status = 'ACTIVE' ORDER BY id", MAPPER);
    }

    public Optional<SportsMatch> findByCode(String matchCode) {
        var list = jdbc.query("SELECT * FROM sports_match WHERE match_code = ?", MAPPER, matchCode);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public void updateExposureState(String matchCode, BigDecimal exposure, boolean limitMode) {
        jdbc.update(
                "UPDATE sports_match SET current_exposure = ?, limit_mode = ?, last_check_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE match_code = ?",
                exposure, limitMode, matchCode);
    }

    public void setLimitMode(String matchCode, boolean limitMode) {
        jdbc.update(
                "UPDATE sports_match SET limit_mode = ?, updated_at = CURRENT_TIMESTAMP WHERE match_code = ?",
                limitMode, matchCode);
    }

    /** ACTIVE | SUSPENDED — SUSPENDED blocks online bet evaluate. */
    public void updateStatus(String matchCode, String status) {
        jdbc.update(
                "UPDATE sports_match SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE match_code = ?",
                status, matchCode);
    }

    /** 批量：全平台 */
    public int updateStatusAll(String status) {
        return jdbc.update(
                "UPDATE sports_match SET status = ?, updated_at = CURRENT_TIMESTAMP",
                status);
    }

    /** 批量：球类 */
    public int updateStatusBySport(String sportCode, String status) {
        return jdbc.update(
                "UPDATE sports_match SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE sport_code = ?",
                status, sportCode);
    }

    /** 批量：联赛 */
    public int updateStatusByLeague(String sportCode, String leagueCode, String status) {
        return jdbc.update(
                """
                UPDATE sports_match SET status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE sport_code = ? AND league_code = ?
                """,
                status, sportCode, leagueCode);
    }

    public int countByStatus(String sportCode, String leagueCode, String status) {
        if (sportCode != null && leagueCode != null) {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sports_match WHERE sport_code = ? AND league_code = ? AND status = ?",
                    Integer.class, sportCode, leagueCode, status);
            return n == null ? 0 : n;
        }
        if (sportCode != null) {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sports_match WHERE sport_code = ? AND status = ?",
                    Integer.class, sportCode, status);
            return n == null ? 0 : n;
        }
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sports_match WHERE status = ?",
                Integer.class, status);
        return n == null ? 0 : n;
    }

    public int countAll(String sportCode, String leagueCode) {
        if (sportCode != null && leagueCode != null) {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sports_match WHERE sport_code = ? AND league_code = ?",
                    Integer.class, sportCode, leagueCode);
            return n == null ? 0 : n;
        }
        if (sportCode != null) {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sports_match WHERE sport_code = ?",
                    Integer.class, sportCode);
            return n == null ? 0 : n;
        }
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sports_match", Integer.class);
        return n == null ? 0 : n;
    }

    public long insert(String matchCode, String home, String away, BigDecimal threshold, BigDecimal delta) {
        return insert(matchCode, home, away, "football", "UNKNOWN", "未分组联赛", threshold, delta);
    }

    public long insert(
            String matchCode, String home, String away,
            String sportCode, String leagueCode, String leagueName,
            BigDecimal threshold, BigDecimal delta) {
        jdbc.update(
                "INSERT INTO sports_match(match_code, home_team, away_team, sport_code, league_code, league_name, exposure_threshold, delta) VALUES(?,?,?,?,?,?,?,?)",
                matchCode, home, away, sportCode, leagueCode, leagueName, threshold, delta);
        return findByCode(matchCode).map(SportsMatch::id).orElse(0L);
    }

    public void updateMeta(
            String matchCode, String home, String away,
            String sportCode, String leagueCode, String leagueName,
            BigDecimal threshold, BigDecimal delta) {
        jdbc.update(
                """
                UPDATE sports_match SET home_team=?, away_team=?, sport_code=?, league_code=?, league_name=?,
                  exposure_threshold=?, delta=?, updated_at=CURRENT_TIMESTAMP
                WHERE match_code=?
                """,
                home, away, sportCode, leagueCode, leagueName, threshold, delta, matchCode);
    }
}
