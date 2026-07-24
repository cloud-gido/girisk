package com.girisk.sports.repository;

import com.girisk.sports.model.SportsMatch;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class SportsMatchRepository {

    private static final RowMapper<SportsMatch> MAPPER = (rs, i) -> new SportsMatch(
            rs.getLong("id"),
            rs.getString("match_code"),
            blankToNull(rs.getString("home_team")),
            blankToNull(rs.getString("away_team")),
            blankToNull(rs.getString("sport_code")),
            blankToNull(rs.getString("league_code")),
            blankToNull(rs.getString("league_name")),
            rs.getBigDecimal("exposure_threshold"),
            rs.getBoolean("limit_mode"),
            rs.getBigDecimal("current_exposure"),
            rs.getBigDecimal("delta"),
            rs.getString("status"),
            rs.getTimestamp("last_check_at") != null ? rs.getTimestamp("last_check_at").toLocalDateTime() : null,
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
    );

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private final JdbcTemplate jdbc;

    public SportsMatchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SportsMatch> findAll() {
        return jdbc.query("SELECT * FROM sports_match ORDER BY updated_at DESC, id DESC", MAPPER);
    }

    public List<SportsMatch> findActive() {
        return jdbc.query("SELECT * FROM sports_match WHERE status = 'ACTIVE' ORDER BY id", MAPPER);
    }

    /**
     * 值班台筛选。空参数忽略。
     * {@code q}：赛事 ID 前缀或对阵模糊；{@code matchCodePrefix}：仅 ID 前缀。
     */
    public List<SportsMatch> findFiltered(
            String sportCode,
            String leagueCode,
            String matchCodePrefix,
            String q,
            String status,
            Boolean limitMode) {
        StringBuilder sql = new StringBuilder("SELECT * FROM sports_match WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (sportCode != null && !sportCode.isBlank()) {
            sql.append(" AND sport_code = ?");
            args.add(sportCode.trim());
        }
        if (leagueCode != null && !leagueCode.isBlank()) {
            sql.append(" AND (league_code = ? OR LOWER(COALESCE(league_name,'')) LIKE LOWER(?))");
            String lc = leagueCode.trim();
            args.add(lc);
            args.add("%" + lc + "%");
        }
        if (matchCodePrefix != null && !matchCodePrefix.isBlank()) {
            sql.append(" AND match_code LIKE ?");
            args.add(matchCodePrefix.trim() + "%");
        }
        if (q != null && !q.isBlank()) {
            String qq = q.trim();
            sql.append(" AND (match_code LIKE ? OR LOWER(COALESCE(home_team,'')) LIKE LOWER(?)");
            sql.append(" OR LOWER(COALESCE(away_team,'')) LIKE LOWER(?)");
            sql.append(" OR LOWER(CONCAT(COALESCE(home_team,''), ' ', COALESCE(away_team,''))) LIKE LOWER(?))");
            args.add(qq + "%");
            args.add("%" + qq + "%");
            args.add("%" + qq + "%");
            args.add("%" + qq + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.trim());
        }
        if (limitMode != null) {
            sql.append(" AND limit_mode = ?");
            args.add(limitMode);
        }
        sql.append(" ORDER BY updated_at DESC, id DESC");
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
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
                matchCode, home, away,
                sportCode == null || sportCode.isBlank() ? "football" : sportCode,
                blankToNull(leagueCode),
                blankToNull(leagueName),
                threshold, delta);
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

    /** 仅展示元数据（队名/球类/联赛），阈值不动。 */
    public void updateDisplayMeta(
            String matchCode, String home, String away,
            String sportCode, String leagueCode, String leagueName) {
        jdbc.update(
                """
                UPDATE sports_match SET home_team=?, away_team=?, sport_code=?, league_code=?, league_name=?,
                  updated_at=CURRENT_TIMESTAMP
                WHERE match_code=?
                """,
                blankToNull(home),
                blankToNull(away),
                sportCode == null || sportCode.isBlank() ? "football" : sportCode.trim(),
                blankToNull(leagueCode),
                blankToNull(leagueName),
                matchCode);
    }
}
