package com.girisk.sports.service;

import com.girisk.configcenter.model.RiskFixtureView;
import com.girisk.flink.RedisFixtureViewReader;
import com.girisk.sports.dto.OutcomeLimitRow;
import com.girisk.sports.dto.SportsDashboardSummary;
import com.girisk.sports.dto.SportsMatchListRow;
import com.girisk.sports.dto.SportsMatchMetaRequest;
import com.girisk.sports.dto.SportsMatchView;
import com.girisk.sports.exposure.GroupLimitSnapshot;
import com.girisk.sports.exposure.ProportionalLimitCalculator;
import com.girisk.sports.model.MarketGroupKey;
import com.girisk.sports.model.SportsMatch;
import com.girisk.sports.repository.SportsMatchRepository;
import com.girisk.sports.store.ExposureStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SportsExposureService {

    private static final Logger log = LoggerFactory.getLogger(SportsExposureService.class);

    private final SportsMatchRepository matchRepository;
    private final ExposureStore exposureStore;
    private final FixtureLimitParamsService limitParamsService;
    private final RedisFixtureViewReader fixtureViewReader;
    private final ScopeGateService scopeGateService;
    private final SportsMatchSyncService matchSyncService;

    public SportsExposureService(
            SportsMatchRepository matchRepository,
            ExposureStore exposureStore,
            FixtureLimitParamsService limitParamsService,
            RedisFixtureViewReader fixtureViewReader,
            ScopeGateService scopeGateService,
            SportsMatchSyncService matchSyncService) {
        this.matchRepository = matchRepository;
        this.exposureStore = exposureStore;
        this.limitParamsService = limitParamsService;
        this.fixtureViewReader = fixtureViewReader;
        this.scopeGateService = scopeGateService;
        this.matchSyncService = matchSyncService;
    }

    public List<SportsMatch> listMatches() {
        matchSyncService.syncFromRedis();
        return matchRepository.findAll();
    }

    /**
     * 值班台列表：先 sync Redis 空壳，再筛选并 enrich 门控/限额/live。
     *
     * @param gateOff trading|limit|exposure — 筛出该开关为关的赛事
     */
    public List<SportsMatchListRow> listMatchRows(
            String sportCode,
            String leagueCode,
            String matchCode,
            String q,
            String status,
            Boolean limitMode,
            String gateOff) {
        matchSyncService.syncFromRedis();
        List<SportsMatch> matches = matchRepository.findFiltered(
                sportCode, leagueCode, matchCode, q, status, limitMode);
        List<SportsMatchListRow> rows = new ArrayList<>(matches.size());
        for (SportsMatch match : matches) {
            SportsMatchListRow row = toListRow(match);
            if (gateOff != null && !gateOff.isBlank() && !matchesGateOff(row, gateOff)) {
                continue;
            }
            rows.add(row);
        }
        return rows;
    }

    private static boolean matchesGateOff(SportsMatchListRow row, String gateOff) {
        String g = gateOff.trim().toLowerCase(Locale.ROOT);
        return switch (g) {
            case "trading" -> !row.tradingEnabled();
            case "limit" -> !row.limitGateEnabled();
            case "exposure" -> !row.exposureGateEnabled();
            default -> true;
        };
    }

    public SportsMatchListRow toListRow(SportsMatch match) {
        FixtureLimitParamsService.EffectiveParams params = limitParamsService.resolve(match);
        ScopeGateService.EffectiveGates gates = scopeGateService.resolveForMatch(match);
        RiskFixtureView flink = fixtureViewReader.findByFixtureId(match.matchCode());

        BigDecimal exposure = match.currentExposure() != null ? match.currentExposure() : BigDecimal.ZERO;
        if (flink != null) {
            List<SportsMatchView.MarketGroupView> groups = mapFlinkMarketGroups(flink.marketGroups());
            BigDecimal fromGroups = sumGroupStakes(groups);
            if (fromGroups.compareTo(BigDecimal.ZERO) > 0) {
                exposure = fromGroups;
            } else if (flink.replayStats() != null && flink.replayStats().get("acceptedStakeYuan") != null) {
                exposure = toBd(flink.replayStats().get("acceptedStakeYuan"));
            }
        }

        return new SportsMatchListRow(
                match.id(),
                match.matchCode(),
                match.homeTeam(),
                match.awayTeam(),
                match.sportOrDefault(),
                match.leagueCode(),
                match.leagueName(),
                params.maxWorstLossYuan(),
                match.limitMode(),
                exposure,
                params.delta(),
                params.seedPayoutYuan(),
                params.maxWorstLossYuan(),
                params.maxBetPayoutYuan(),
                params.overrideActive(),
                match.status(),
                match.lastCheckAt(),
                match.updatedAt(),
                gates.tradingEnabled(),
                gates.limitGateEnabled(),
                gates.exposureGateEnabled(),
                gates.tradingSource(),
                gates.limitGateSource(),
                gates.exposureGateSource(),
                flink != null ? flink.liveScore() : null,
                flink != null ? flink.worstScore() : null,
                flink != null ? flink.worstLossCents() : null,
                flink != null ? flink.riskLevel() : null,
                flink != null ? flink.confirmedOrders() : null);
    }

    public SportsMatchView updateMeta(String matchCode, SportsMatchMetaRequest req) {
        SportsMatch match = matchRepository.findByCode(matchCode)
                .orElseThrow(() -> new com.girisk.common.exception.BusinessException("比赛不存在: " + matchCode));
        if (req == null) {
            return toView(match);
        }
        matchRepository.updateDisplayMeta(
                matchCode,
                req.homeTeam(),
                req.awayTeam(),
                req.sportCode() != null ? req.sportCode() : match.sportOrDefault(),
                req.leagueCode(),
                req.leagueName());
        return getMatchView(matchCode);
    }

    public SportsDashboardSummary buildDashboard() {
        matchSyncService.syncFromRedis();
        List<SportsMatch> matches = matchRepository.findAll();
        int outcomeCount = 0;
        int overLimitOutcomeCount = 0;
        int limitModeMatchCount = 0;
        BigDecimal totalStake = BigDecimal.ZERO;
        List<SportsDashboardSummary.OverLimitOutcomeItem> overItems = new ArrayList<>();

        for (SportsMatch match : matches) {
            SportsMatchView view = toView(match);
            totalStake = totalStake.add(view.currentExposure());
            if (view.limitMode()) {
                limitModeMatchCount++;
            }
            for (SportsMatchView.MarketGroupView group : view.marketGroups()) {
                for (var row : group.outcomes()) {
                    outcomeCount++;
                    if (row.stake().compareTo(row.maxAllowedAmount()) > 0) {
                        overLimitOutcomeCount++;
                        overItems.add(new SportsDashboardSummary.OverLimitOutcomeItem(
                                view.matchCode(),
                                nullToDash(view.homeTeam()),
                                nullToDash(view.awayTeam()),
                                group.marketType(), group.marketLabel(), group.line(), row.selection(),
                                row.stake(), row.maxAllowedAmount()));
                    }
                }
            }
        }

        return new SportsDashboardSummary(
                matches.size(),
                outcomeCount,
                overLimitOutcomeCount,
                limitModeMatchCount,
                totalStake,
                matches,
                overItems);
    }

    public SportsMatchView getMatchView(String matchCode) {
        SportsMatch match = matchRepository.findByCode(matchCode)
                .orElseThrow(() -> new com.girisk.common.exception.BusinessException("比赛不存在: " + matchCode));
        return toView(match);
    }

    public void runExposureCheckForAll() {
        for (SportsMatch match : matchRepository.findActive()) {
            runExposureCheck(match.matchCode());
        }
    }

    public void runExposureCheck(String matchCode) {
        SportsMatch match = matchRepository.findByCode(matchCode)
                .orElseThrow(() -> new com.girisk.common.exception.BusinessException("比赛不存在"));
        BigDecimal exposure = exposureStore.getMatchTotalStake(matchCode);
        boolean limitMode = exposure.compareTo(match.exposureThreshold()) >= 0;
        matchRepository.updateExposureState(matchCode, exposure, limitMode);
        log.info("Exposure check match={} exposure={} threshold={} limitMode={}",
                matchCode, exposure, match.exposureThreshold(), limitMode);
    }

    public GroupLimitSnapshot calcGroupLimitSnapshot(MarketGroupKey key, double delta) {
        return calcGroupLimitSnapshot(key, delta, BigDecimal.ZERO);
    }

    /**
     * Console HTTP decide / 演示用：按 ExposureStore 计算限额。
     * 责任盘生产视图请用 {@link #toView}（Flink Redis marketGroups）。
     */
    public GroupLimitSnapshot calcGroupLimitSnapshot(
            MarketGroupKey key, double delta, BigDecimal seedPayoutYuan) {
        Map<String, BigDecimal> payouts = exposureStore.getGroupPayouts(key);
        Map<String, BigDecimal> stakes = exposureStore.getGroupStakes(key);
        String[] selections = key.marketType().selections();
        BigDecimal seed = seedPayoutYuan == null ? BigDecimal.ZERO : seedPayoutYuan;
        List<BigDecimal> actual = new ArrayList<>();
        List<BigDecimal> withSeed = new ArrayList<>();
        for (String sel : selections) {
            BigDecimal p = payouts.getOrDefault(sel, null);
            if (p == null) {
                p = stakes.getOrDefault(sel, BigDecimal.ZERO);
            }
            actual.add(p);
            withSeed.add(p.add(seed));
        }
        List<ProportionalLimitCalculator.LimitResult> results =
                ProportionalLimitCalculator.calcAll(withSeed, delta);
        return GroupLimitSnapshot.from(selections, actual, results);
    }

    public Map<String, BigDecimal> calcGroupLimits(MarketGroupKey key, double delta) {
        return calcGroupLimitSnapshot(key, delta).acceptMax();
    }

    public SportsMatchView toView(SportsMatch match) {
        FixtureLimitParamsService.EffectiveParams params = limitParamsService.resolve(match);
        RiskFixtureView flinkView = fixtureViewReader.findByFixtureId(match.matchCode());

        BigDecimal deltaBd = params.delta();
        BigDecimal seed = params.seedPayoutYuan() == null ? BigDecimal.ZERO : params.seedPayoutYuan();
        // δ / 种子 / 阈值一律用 Console 分层生效值，不用 Flink Redis 决策快照覆盖（避免整场与赛前/滚球 Tab 不一致）

        List<SportsMatchView.MarketGroupView> groups = mapFlinkMarketGroups(
                flinkView == null ? null : flinkView.marketGroups());

        BigDecimal exposure = sumGroupStakes(groups);
        if (exposure.compareTo(BigDecimal.ZERO) == 0 && flinkView != null
                && flinkView.replayStats() != null
                && flinkView.replayStats().get("acceptedStakeYuan") != null) {
            exposure = toBd(flinkView.replayStats().get("acceptedStakeYuan"));
        }

        return new SportsMatchView(
                match.id(), match.matchCode(), match.homeTeam(), match.awayTeam(),
                match.sportOrDefault(), match.leagueCode(), match.leagueName(),
                params.maxWorstLossYuan(), match.limitMode(), exposure, deltaBd,
                seed, params.maxWorstLossYuan(), params.maxBetPayoutYuan(),
                params.overrideActive(),
                match.status(), match.lastCheckAt(), groups);
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    /**
     * 责任盘只认 Flink Redis {@code marketGroups}；无数据时返回空列表（不回退 ExposureStore）。
     */
    @SuppressWarnings("unchecked")
    static List<SportsMatchView.MarketGroupView> mapFlinkMarketGroups(List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<SportsMatchView.MarketGroupView> out = new ArrayList<>();
        for (Map<String, Object> g : raw) {
            if (g == null) {
                continue;
            }
            String marketType = str(g.get("marketType"));
            String marketLabel = str(g.get("marketLabel"));
            if (marketLabel.isBlank()) {
                marketLabel = marketType;
            }
            String line = str(g.get("line"));
            Object outcomesObj = g.get("outcomes");
            if (!(outcomesObj instanceof List<?> outcomesList)) {
                continue;
            }
            Map<String, BigDecimal> stakes = new LinkedHashMap<>();
            Map<String, BigDecimal> limits = new LinkedHashMap<>();
            List<OutcomeLimitRow> rows = new ArrayList<>();
            for (Object o : outcomesList) {
                if (!(o instanceof Map<?, ?> om)) {
                    continue;
                }
                Map<String, Object> row = (Map<String, Object>) om;
                String selection = str(row.get("selection"));
                if (selection.isBlank()) {
                    continue;
                }
                BigDecimal stake = toBd(row.get("stake"));
                BigDecimal actualStake = row.containsKey("actualStake") ? toBd(row.get("actualStake")) : null;
                BigDecimal seedYuan = row.containsKey("seedYuan") ? toBd(row.get("seedYuan")) : null;
                BigDecimal target = toBd(row.get("targetAmount"));
                BigDecimal maxAllowed = toBd(row.get("maxAllowedAmount"));
                BigDecimal acceptMax = toBd(row.get("acceptMax"));
                stakes.put(selection, stake);
                limits.put(selection, acceptMax);
                rows.add(
                        new OutcomeLimitRow(
                                selection, stake, target, maxAllowed, acceptMax, actualStake, seedYuan));
            }
            if (rows.isEmpty()) {
                continue;
            }
            out.add(new SportsMatchView.MarketGroupView(
                    marketType, marketLabel, line, stakes, limits, rows));
        }
        return out;
    }

    /**
     * 列表/详情「当前敞口」用真实已投注（不含冷启动）。Flink {@code stake} 是含种子账面，优先
     * {@code actualStake}；否则 {@code stake - seedYuan}；再缺省才回退 stakes map。
     */
    static BigDecimal sumGroupStakes(List<SportsMatchView.MarketGroupView> groups) {
        BigDecimal sum = BigDecimal.ZERO;
        for (SportsMatchView.MarketGroupView g : groups) {
            if (g.outcomes() != null && !g.outcomes().isEmpty()) {
                for (OutcomeLimitRow row : g.outcomes()) {
                    sum = sum.add(actualStakeOf(row));
                }
            } else {
                for (BigDecimal v : g.stakes().values()) {
                    sum = sum.add(v == null ? BigDecimal.ZERO : v);
                }
            }
        }
        return sum;
    }

    private static BigDecimal actualStakeOf(OutcomeLimitRow row) {
        if (row.actualStake() != null) {
            return row.actualStake();
        }
        BigDecimal book = row.stake() == null ? BigDecimal.ZERO : row.stake();
        if (row.seedYuan() != null) {
            return book.subtract(row.seedYuan()).max(BigDecimal.ZERO);
        }
        return book;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        try {
            return new BigDecimal(o.toString()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
