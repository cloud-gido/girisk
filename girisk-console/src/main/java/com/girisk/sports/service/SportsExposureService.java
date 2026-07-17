package com.girisk.sports.service;

import com.girisk.sports.dto.SportsDashboardSummary;
import com.girisk.sports.dto.SportsMatchView;
import com.girisk.sports.exposure.GroupLimitSnapshot;
import com.girisk.sports.exposure.ProportionalLimitCalculator;
import com.girisk.sports.model.MarketGroupKey;
import com.girisk.sports.model.SportsMarketType;
import com.girisk.sports.model.SportsMatch;
import com.girisk.sports.repository.SportsMatchRepository;
import com.girisk.sports.store.ExposureStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SportsExposureService {

    private static final Logger log = LoggerFactory.getLogger(SportsExposureService.class);

    private final SportsMatchRepository matchRepository;
    private final ExposureStore exposureStore;
    private final FixtureLimitParamsService limitParamsService;

    public SportsExposureService(
            SportsMatchRepository matchRepository,
            ExposureStore exposureStore,
            FixtureLimitParamsService limitParamsService) {
        this.matchRepository = matchRepository;
        this.exposureStore = exposureStore;
        this.limitParamsService = limitParamsService;
    }

    public List<SportsMatch> listMatches() {
        return matchRepository.findAll();
    }

    public SportsDashboardSummary buildDashboard() {
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
                                view.matchCode(), view.homeTeam(), view.awayTeam(),
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
        Map<String, BigDecimal> stakes = exposureStore.getGroupStakes(key);
        String[] selections = key.marketType().selections();
        List<BigDecimal> amounts = new ArrayList<>();
        for (String sel : selections) {
            amounts.add(stakes.getOrDefault(sel, BigDecimal.ZERO));
        }
        List<ProportionalLimitCalculator.LimitResult> results =
                ProportionalLimitCalculator.calcAll(amounts, delta);
        return GroupLimitSnapshot.from(selections, amounts, results);
    }

    public Map<String, BigDecimal> calcGroupLimits(MarketGroupKey key, double delta) {
        return calcGroupLimitSnapshot(key, delta).acceptMax();
    }

    public SportsMatchView toView(SportsMatch match) {
        FixtureLimitParamsService.EffectiveParams params = limitParamsService.resolve(match);
        double delta = params.delta().doubleValue();

        List<SportsMatchView.MarketGroupView> groups = new ArrayList<>();
        groups.add(buildGroupView(match.matchCode(), SportsMarketType.ONE_X_TWO, "", delta));
        groups.add(buildGroupView(match.matchCode(), SportsMarketType.OVER_UNDER, "3", delta));
        groups.add(buildGroupView(match.matchCode(), SportsMarketType.HANDICAP, "1", delta));

        BigDecimal exposure = exposureStore.getMatchTotalStake(match.matchCode());
        return new SportsMatchView(
                match.id(), match.matchCode(), match.homeTeam(), match.awayTeam(),
                match.sportOrDefault(), match.leagueCodeOrDefault(), match.leagueNameOrDefault(),
                params.maxWorstLossYuan(), match.limitMode(), exposure, params.delta(),
                params.seedPayoutYuan(), params.maxWorstLossYuan(), params.maxBetPayoutYuan(),
                params.overrideActive(),
                match.status(), match.lastCheckAt(), groups);
    }

    private SportsMatchView.MarketGroupView buildGroupView(
            String matchCode, SportsMarketType type, String line, double delta) {
        MarketGroupKey key = MarketGroupKey.of(matchCode, type, line);
        GroupLimitSnapshot snapshot = calcGroupLimitSnapshot(key, delta);
        return new SportsMatchView.MarketGroupView(
                type.name(), type.label(), line,
                snapshot.stakes(), snapshot.acceptMax(), snapshot.rows());
    }
}
