package com.girisk.sports.service;

import com.girisk.sports.model.MarketGroupKey;
import com.girisk.sports.model.SportsMarketType;
import com.girisk.sports.model.SportsMatch;
import com.girisk.sports.repository.SportsMatchRepository;
import com.girisk.sports.store.ExposureStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Seeds sports_match + ExposureStore from local Flink-parity replay output. */
@Service
public class SportsReplaySeedService {

    private final SportsMatchRepository matchRepository;
    private final ExposureStore exposureStore;
    private final SportsExposureService exposureService;

    public SportsReplaySeedService(
            SportsMatchRepository matchRepository,
            ExposureStore exposureStore,
            SportsExposureService exposureService) {
        this.matchRepository = matchRepository;
        this.exposureStore = exposureStore;
        this.exposureService = exposureService;
    }

    public Map<String, Object> seed(ReplaySeedRequest req) {
        String matchCode = req.matchCode();
        BigDecimal threshold = req.exposureThreshold() != null ? req.exposureThreshold() : new BigDecimal("200000");
        BigDecimal delta = req.delta() != null ? req.delta() : new BigDecimal("0.2");
        String sport = blank(req.sportCode(), "football");
        String leagueCode = blank(req.leagueCode(), "FRIENDLY");
        String leagueName = blank(req.leagueName(), "国际友谊");

        if (matchRepository.findByCode(matchCode).isEmpty()) {
            matchRepository.insert(
                    matchCode,
                    req.homeTeam(),
                    req.awayTeam(),
                    sport,
                    leagueCode,
                    leagueName,
                    threshold,
                    delta);
        } else {
            matchRepository.updateMeta(
                    matchCode, req.homeTeam(), req.awayTeam(), sport, leagueCode, leagueName, threshold, delta);
        }

        exposureStore.clearMatch(matchCode);
        int groups = 0;
        if (req.groups() != null) {
            for (GroupSeed g : req.groups()) {
                SportsMarketType type = SportsMarketType.from(g.marketType());
                MarketGroupKey key = MarketGroupKey.of(matchCode, type, g.line());
                if (g.stakes() != null && !g.stakes().isEmpty()) {
                    exposureStore.seedGroup(key, toBigDecimalMap(g.stakes()));
                }
                if (g.payouts() != null && !g.payouts().isEmpty()) {
                    exposureStore.seedGroupPayouts(key, toBigDecimalMap(g.payouts()));
                }
                groups++;
            }
        }

        exposureService.runExposureCheck(matchCode);
        SportsMatch match = matchRepository.findByCode(matchCode).orElseThrow();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("matchCode", match.matchCode());
        out.put("homeTeam", match.homeTeam());
        out.put("awayTeam", match.awayTeam());
        out.put("groupsSeeded", groups);
        out.put("totalStake", exposureStore.getMatchTotalStake(matchCode));
        out.put("limitMode", match.limitMode());
        return out;
    }

    private static Map<String, BigDecimal> toBigDecimalMap(Map<String, ?> raw) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        raw.forEach((k, v) -> m.put(k, new BigDecimal(v.toString())));
        return m;
    }

    private static String blank(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    public record ReplaySeedRequest(
            String matchCode,
            String homeTeam,
            String awayTeam,
            String sportCode,
            String leagueCode,
            String leagueName,
            BigDecimal delta,
            BigDecimal exposureThreshold,
            List<GroupSeed> groups) {}

    public record GroupSeed(
            String marketType, String line, Map<String, Object> stakes, Map<String, Object> payouts) {}
}
