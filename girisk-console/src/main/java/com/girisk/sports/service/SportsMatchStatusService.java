package com.girisk.sports.service;

import com.girisk.common.exception.BusinessException;
import com.girisk.sports.dto.SportsMatchView;
import com.girisk.sports.model.LimitScopeType;
import com.girisk.sports.model.ScopeLimitOverride;
import com.girisk.sports.repository.SportsMatchRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SportsMatchStatusService {

    private static final Set<String> ALLOWED = Set.of("ACTIVE", "SUSPENDED");

    private final SportsMatchRepository matchRepository;
    private final SportsExposureService exposureService;
    private final ScopeGateService scopeGateService;
    private final ScopeDutyAuth dutyAuth;

    public SportsMatchStatusService(
            SportsMatchRepository matchRepository,
            SportsExposureService exposureService,
            ScopeGateService scopeGateService,
            ScopeDutyAuth dutyAuth) {
        this.matchRepository = matchRepository;
        this.exposureService = exposureService;
        this.scopeGateService = scopeGateService;
        this.dutyAuth = dutyAuth;
    }

    public SportsMatchView setStatus(String matchCode, String status) {
        dutyAuth.requireWrite(LimitScopeType.MATCH);
        if (matchCode == null || matchCode.isBlank()) {
            throw new BusinessException("matchCode required");
        }
        if (matchRepository.findByCode(matchCode).isEmpty()) {
            throw new BusinessException("比赛不存在: " + matchCode);
        }
        String normalized = normalizeStatus(status);
        matchRepository.updateStatus(matchCode, normalized);
        scopeGateService.mirrorTrading(
                LimitScopeType.MATCH, matchCode, "ACTIVE".equals(normalized), dutyAuth.currentUsername());
        return exposureService.getMatchView(matchCode);
    }

    /**
     * 按层批量停盘/开盘：写入该层下全部 sports_match.status，并镜像 trading 门控。
     */
    public Map<String, Object> setScopeStatus(LimitScopeType type, String scopeKey, String status) {
        dutyAuth.requireWrite(type);
        String normalized = normalizeStatus(status);
        int updated;
        String sport = null;
        String league = null;
        String key = scopeKey;
        switch (type) {
            case OVERALL -> {
                key = "_";
                updated = matchRepository.updateStatusAll(normalized);
            }
            case SPORT -> {
                sport = blankToNull(scopeKey);
                if (sport == null) {
                    throw new BusinessException("sport scopeKey required");
                }
                key = sport;
                updated = matchRepository.updateStatusBySport(sport, normalized);
            }
            case LEAGUE -> {
                String[] parts = ScopeLimitOverride.splitLeagueKey(scopeKey);
                sport = parts[0];
                league = parts[1];
                key = ScopeLimitOverride.leagueKey(sport, league);
                updated = matchRepository.updateStatusByLeague(sport, league, normalized);
            }
            case MATCH -> throw new BusinessException("赛事停盘请用 POST /matches/{matchCode}/status");
            default -> throw new BusinessException("未知 scopeType");
        }
        scopeGateService.mirrorTrading(type, key, "ACTIVE".equals(normalized), dutyAuth.currentUsername());
        int total = matchRepository.countAll(sport, league);
        int suspended = matchRepository.countByStatus(sport, league, "SUSPENDED");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scopeType", type.name());
        out.put("scopeKey", key);
        out.put("status", normalized);
        out.put("updated", updated);
        out.put("matchCount", total);
        out.put("suspendedCount", suspended);
        out.put("activeCount", Math.max(0, total - suspended));
        return out;
    }

    public Map<String, Object> scopeStatusSummary(LimitScopeType type, String scopeKey) {
        String sport = null;
        String league = null;
        switch (type) {
            case OVERALL -> { /* all */ }
            case SPORT -> sport = blankToNull(scopeKey);
            case LEAGUE -> {
                String[] parts = ScopeLimitOverride.splitLeagueKey(scopeKey);
                sport = parts[0];
                league = parts[1];
            }
            case MATCH -> throw new BusinessException("赛事状态请查单场接口");
            default -> throw new BusinessException("未知 scopeType");
        }
        if (type == LimitScopeType.SPORT && sport == null) {
            throw new BusinessException("sport scopeKey required");
        }
        int total = matchRepository.countAll(sport, league);
        int suspended = matchRepository.countByStatus(sport, league, "SUSPENDED");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scopeType", type.name());
        out.put("scopeKey", scopeKey == null || scopeKey.isBlank() ? "_" : scopeKey);
        out.put("matchCount", total);
        out.put("suspendedCount", suspended);
        out.put("activeCount", Math.max(0, total - suspended));
        return out;
    }

    private static String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED.contains(normalized)) {
            throw new BusinessException("status 仅支持 ACTIVE 或 SUSPENDED");
        }
        return normalized;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() || "_".equals(s) ? null : s.trim();
    }
}
