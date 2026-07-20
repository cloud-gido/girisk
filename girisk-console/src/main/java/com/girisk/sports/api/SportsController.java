package com.girisk.sports.api;

import com.girisk.common.dto.ApiResponse;
import com.girisk.common.enums.RiskDecision;
import com.girisk.gateway.RiskDecisionGateway;
import com.girisk.gateway.RiskDecisionRequest;
import com.girisk.gateway.RiskDecisionResponse;
import com.girisk.sports.dto.FixtureLimitOverrideRequest;
import com.girisk.sports.dto.FixtureLimitParamsView;
import com.girisk.sports.dto.SportsDashboardSummary;
import com.girisk.sports.dto.SportsBetEvaluateRequest;
import com.girisk.sports.dto.SportsBetEvaluateResponse;
import com.girisk.sports.dto.SportsMatchView;
import com.girisk.sports.model.SportsBetLog;
import com.girisk.sports.model.SportsMatch;
import com.girisk.sports.repository.SportsBetLogRepository;
import com.girisk.sports.repository.SportsMatchRepository;
import com.girisk.sports.dto.ScopeGateOverrideRequest;
import com.girisk.sports.dto.ScopeGateParamsView;
import com.girisk.sports.dto.ScopeLimitParamsView;
import com.girisk.sports.model.LimitScopeType;
import com.girisk.sports.model.ScopeLimitOverride;
import com.girisk.sports.service.ExposureDemoBootstrapService;
import com.girisk.sports.service.FixtureLimitParamsService;
import com.girisk.sports.service.ScopeDutyAuth;
import com.girisk.sports.service.ScopeGateService;
import com.girisk.sports.service.ScopeLimitParamsService;
import com.girisk.sports.service.ScopeRiskConfigBootstrapSync;
import com.girisk.sports.service.SportsExposureService;
import com.girisk.sports.service.SportsMatchStatusService;
import com.girisk.sports.service.SportsReplaySeedService;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sports")
public class SportsController {

    private final RiskDecisionGateway gateway;
    private final SportsExposureService exposureService;
    private final SportsReplaySeedService replaySeedService;
    private final FixtureLimitParamsService limitParamsService;
    private final ScopeLimitParamsService scopeLimitParamsService;
    private final ScopeGateService scopeGateService;
    private final ExposureDemoBootstrapService demoBootstrapService;
    private final SportsMatchStatusService matchStatusService;
    private final SportsBetLogRepository betLogRepository;
    private final SportsMatchRepository matchRepository;
    private final ScopeDutyAuth dutyAuth;
    private final ScopeRiskConfigBootstrapSync configBootstrapSync;

    public SportsController(
            RiskDecisionGateway gateway,
            SportsExposureService exposureService,
            SportsReplaySeedService replaySeedService,
            FixtureLimitParamsService limitParamsService,
            ScopeLimitParamsService scopeLimitParamsService,
            ScopeGateService scopeGateService,
            ExposureDemoBootstrapService demoBootstrapService,
            SportsMatchStatusService matchStatusService,
            SportsBetLogRepository betLogRepository,
            SportsMatchRepository matchRepository,
            ScopeDutyAuth dutyAuth,
            ObjectProvider<ScopeRiskConfigBootstrapSync> configBootstrapSync) {
        this.gateway = gateway;
        this.exposureService = exposureService;
        this.replaySeedService = replaySeedService;
        this.limitParamsService = limitParamsService;
        this.scopeLimitParamsService = scopeLimitParamsService;
        this.scopeGateService = scopeGateService;
        this.demoBootstrapService = demoBootstrapService;
        this.matchStatusService = matchStatusService;
        this.betLogRepository = betLogRepository;
        this.matchRepository = matchRepository;
        this.dutyAuth = dutyAuth;
        this.configBootstrapSync = configBootstrapSync.getIfAvailable();
    }

    /**
     * @deprecated 请使用 {@code POST /api/v1/girisk/decide}。须带 userId/operatorId。
     */
    @Deprecated
    @PostMapping("/bet/evaluate")
    public ApiResponse<SportsBetEvaluateResponse> evaluate(@Valid @RequestBody SportsBetEvaluateRequest request) {
        if (request.userId() == null || request.userId().isBlank()) {
            return ApiResponse.fail("体育投注必须提供 userId，请改用 POST /api/v1/girisk/decide");
        }
        if (request.operatorId() == null || request.operatorId().isBlank()) {
            return ApiResponse.fail("体育投注必须提供 operatorId，请改用 POST /api/v1/girisk/decide");
        }
        RiskDecisionRequest unified = RiskDecisionRequest.fromSports(request, request.userId(), request.operatorId());
        RiskDecisionResponse resp = gateway.decide(unified, "LEGACY_SPORTS");
        return ApiResponse.ok(toSportsResponse(resp, request));
    }

    private static SportsBetEvaluateResponse toSportsResponse(RiskDecisionResponse resp, SportsBetEvaluateRequest req) {
        Map<String, Object> detail = resp.sportsDetail() != null ? resp.sportsDetail() : Map.of();
        BigDecimal bMax = detail.get("bMaxYuan") instanceof BigDecimal b ? b
                : (detail.get("bMaxYuan") != null ? new BigDecimal(detail.get("bMaxYuan").toString()) : BigDecimal.ZERO);
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> stakes = detail.get("groupStakes") instanceof Map<?, ?> m
                ? (Map<String, BigDecimal>) m : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> limits = detail.get("groupLimits") instanceof Map<?, ?> m
                ? (Map<String, BigDecimal>) m : Map.of();
        BigDecimal exposure = detail.get("matchExposure") instanceof BigDecimal e ? e : BigDecimal.ZERO;
        String decision = resp.decision() == RiskDecision.LIMIT ? "REJECT" : resp.decision().name();
        return new SportsBetEvaluateResponse(
                resp.requestId(), req.orderId(), decision, resp.reason(),
                Boolean.TRUE.equals(resp.limitMode()), req.amount(), bMax,
                stakes.getOrDefault(req.selection(), BigDecimal.ZERO),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                SportsBetEvaluateResponse.stakesMap(stakes),
                SportsBetEvaluateResponse.stakesMap(limits),
                Map.of(), Map.of(),
                exposure, BigDecimal.ZERO, resp.latencyMs());
    }

    @GetMapping("/dashboard")
    public ApiResponse<SportsDashboardSummary> dashboard() {
        return ApiResponse.ok(exposureService.buildDashboard());
    }

    @GetMapping("/matches")
    public ApiResponse<List<SportsMatch>> listMatches() {
        return ApiResponse.ok(exposureService.listMatches());
    }

    @GetMapping("/matches/{matchCode}")
    public ApiResponse<SportsMatchView> getMatch(@PathVariable String matchCode) {
        return ApiResponse.ok(exposureService.getMatchView(matchCode));
    }

    @PostMapping("/matches/{matchCode}/exposure-check")
    public ApiResponse<SportsMatchView> triggerExposureCheck(@PathVariable String matchCode) {
        exposureService.runExposureCheck(matchCode);
        return ApiResponse.ok(exposureService.getMatchView(matchCode));
    }

    /** 场次有效限额参数（覆盖 ⊕ 赛事库 ⊕ 全局）。 */
    @GetMapping("/matches/{matchCode}/limit-override")
    public ApiResponse<FixtureLimitParamsView> getLimitOverride(@PathVariable String matchCode) {
        return ApiResponse.ok(limitParamsService.getView(matchCode));
    }

    /**
     * 交易员场次级限额覆盖：δ / 种子 / 最差亏损阈值 / 单注返彩上限。
     * 字段传 null 表示该项不覆盖；全部 null 等同清除。立即影响看板试算与在线裁决（online-decide 开启时）。
     */
    @PutMapping("/matches/{matchCode}/limit-override")
    public ApiResponse<FixtureLimitParamsView> putLimitOverride(
            @PathVariable String matchCode,
            @RequestBody FixtureLimitOverrideRequest body) {
        return ApiResponse.ok(limitParamsService.upsert(matchCode, body));
    }

    @DeleteMapping("/matches/{matchCode}/limit-override")
    public ApiResponse<FixtureLimitParamsView> clearLimitOverride(@PathVariable String matchCode) {
        return ApiResponse.ok(limitParamsService.clear(matchCode));
    }

    /**
     * 层级限额覆盖：overall | sport | league。
     * scopeKey：overall 用 {@code _}；sport 用球类码；league 用 {@code sport:leagueCode}。
     */
    @GetMapping("/scopes/{scopeType}/{scopeKey}/limit-override")
    public ApiResponse<ScopeLimitParamsView> getScopeLimitOverride(
            @PathVariable String scopeType,
            @PathVariable String scopeKey) {
        return ApiResponse.ok(scopeLimitParamsService.getView(LimitScopeType.from(scopeType), scopeKey));
    }

    @PutMapping("/scopes/{scopeType}/{scopeKey}/limit-override")
    public ApiResponse<ScopeLimitParamsView> putScopeLimitOverride(
            @PathVariable String scopeType,
            @PathVariable String scopeKey,
            @RequestBody FixtureLimitOverrideRequest body) {
        return ApiResponse.ok(scopeLimitParamsService.upsert(LimitScopeType.from(scopeType), scopeKey, body));
    }

    @DeleteMapping("/scopes/{scopeType}/{scopeKey}/limit-override")
    public ApiResponse<ScopeLimitParamsView> clearScopeLimitOverride(
            @PathVariable String scopeType,
            @PathVariable String scopeKey) {
        return ApiResponse.ok(scopeLimitParamsService.clear(LimitScopeType.from(scopeType), scopeKey));
    }

    /** 便捷：联赛 key = sport:leagueCode */
    @GetMapping("/scopes/league/{sportCode}/{leagueCode}/limit-override")
    public ApiResponse<ScopeLimitParamsView> getLeagueLimitOverride(
            @PathVariable String sportCode,
            @PathVariable String leagueCode) {
        return ApiResponse.ok(scopeLimitParamsService.getView(
                LimitScopeType.LEAGUE, ScopeLimitOverride.leagueKey(sportCode, leagueCode)));
    }

    @PutMapping("/scopes/league/{sportCode}/{leagueCode}/limit-override")
    public ApiResponse<ScopeLimitParamsView> putLeagueLimitOverride(
            @PathVariable String sportCode,
            @PathVariable String leagueCode,
            @RequestBody FixtureLimitOverrideRequest body) {
        return ApiResponse.ok(scopeLimitParamsService.upsert(
                LimitScopeType.LEAGUE, ScopeLimitOverride.leagueKey(sportCode, leagueCode), body));
    }

    @DeleteMapping("/scopes/league/{sportCode}/{leagueCode}/limit-override")
    public ApiResponse<ScopeLimitParamsView> clearLeagueLimitOverride(
            @PathVariable String sportCode,
            @PathVariable String leagueCode) {
        return ApiResponse.ok(scopeLimitParamsService.clear(
                LimitScopeType.LEAGUE, ScopeLimitOverride.leagueKey(sportCode, leagueCode)));
    }

    /**
     * 层级门控：总开关 / 限额开关 / 敞口开关。
     * 继承：MATCH &gt; LEAGUE &gt; SPORT &gt; OVERALL &gt; 默认全开。
     */
    @GetMapping("/scopes/{scopeType}/{scopeKey}/gates")
    public ApiResponse<ScopeGateParamsView> getScopeGates(
            @PathVariable String scopeType,
            @PathVariable String scopeKey) {
        return ApiResponse.ok(scopeGateService.getView(LimitScopeType.from(scopeType), scopeKey));
    }

    @PutMapping("/scopes/{scopeType}/{scopeKey}/gates")
    public ApiResponse<ScopeGateParamsView> putScopeGates(
            @PathVariable String scopeType,
            @PathVariable String scopeKey,
            @RequestBody ScopeGateOverrideRequest body) {
        return ApiResponse.ok(scopeGateService.upsert(LimitScopeType.from(scopeType), scopeKey, body));
    }

    @DeleteMapping("/scopes/{scopeType}/{scopeKey}/gates")
    public ApiResponse<ScopeGateParamsView> clearScopeGates(
            @PathVariable String scopeType,
            @PathVariable String scopeKey) {
        return ApiResponse.ok(scopeGateService.clear(LimitScopeType.from(scopeType), scopeKey));
    }

    @GetMapping("/scopes/league/{sportCode}/{leagueCode}/gates")
    public ApiResponse<ScopeGateParamsView> getLeagueGates(
            @PathVariable String sportCode,
            @PathVariable String leagueCode) {
        return ApiResponse.ok(scopeGateService.getView(
                LimitScopeType.LEAGUE, ScopeLimitOverride.leagueKey(sportCode, leagueCode)));
    }

    @PutMapping("/scopes/league/{sportCode}/{leagueCode}/gates")
    public ApiResponse<ScopeGateParamsView> putLeagueGates(
            @PathVariable String sportCode,
            @PathVariable String leagueCode,
            @RequestBody ScopeGateOverrideRequest body) {
        return ApiResponse.ok(scopeGateService.upsert(
                LimitScopeType.LEAGUE, ScopeLimitOverride.leagueKey(sportCode, leagueCode), body));
    }

    @DeleteMapping("/scopes/league/{sportCode}/{leagueCode}/gates")
    public ApiResponse<ScopeGateParamsView> clearLeagueGates(
            @PathVariable String sportCode,
            @PathVariable String leagueCode) {
        return ApiResponse.ok(scopeGateService.clear(
                LimitScopeType.LEAGUE, ScopeLimitOverride.leagueKey(sportCode, leagueCode)));
    }

    @GetMapping("/matches/{matchCode}/gates")
    public ApiResponse<ScopeGateParamsView> getMatchGates(@PathVariable String matchCode) {
        return ApiResponse.ok(scopeGateService.getMatchView(matchCode));
    }

    @PutMapping("/matches/{matchCode}/gates")
    public ApiResponse<ScopeGateParamsView> putMatchGates(
            @PathVariable String matchCode,
            @RequestBody ScopeGateOverrideRequest body) {
        return ApiResponse.ok(scopeGateService.upsert(LimitScopeType.MATCH, matchCode, body));
    }

    @DeleteMapping("/matches/{matchCode}/gates")
    public ApiResponse<ScopeGateParamsView> clearMatchGates(@PathVariable String matchCode) {
        return ApiResponse.ok(scopeGateService.clear(LimitScopeType.MATCH, matchCode));
    }

    /**
     * 运维：把 Redis 全部值班覆盖重刷到 {@code girisk.config.v1}（修复漂移）。
     * 仅 ADMIN。
     */
    @PostMapping("/scopes/config-sync")
    public ApiResponse<Map<String, Object>> syncConfigToKafka() {
        dutyAuth.requireWrite(LimitScopeType.OVERALL);
        if (configBootstrapSync == null) {
            return ApiResponse.fail("Kafka 未启用，无法同步");
        }
        return ApiResponse.ok(configBootstrapSync.syncAll());
    }

    @PostMapping("/matches")
    public ApiResponse<Map<String, Object>> createMatch(@RequestBody Map<String, Object> body) {
        String code = String.valueOf(body.get("matchCode"));
        String home = String.valueOf(body.get("homeTeam"));
        String away = String.valueOf(body.get("awayTeam"));
        BigDecimal threshold = new BigDecimal(String.valueOf(body.getOrDefault("exposureThreshold", "50000")));
        BigDecimal delta = new BigDecimal(String.valueOf(body.getOrDefault("delta", "0.2")));
        if (matchRepository.findByCode(code).isPresent()) {
            return ApiResponse.fail("比赛已存在: " + code);
        }
        long id = matchRepository.insert(code, home, away, threshold, delta);
        return ApiResponse.ok(Map.of("id", id, "matchCode", code));
    }

    /**
     * 本地回放灌数：写入 sports_match + 盘口 stake/payout，供敞口看板下钻。
     * Body 由 LocalExposureReplayMain --seed-out 生成。
     */
    @PostMapping("/replay/seed")
    public ApiResponse<Map<String, Object>> seedReplay(@RequestBody SportsReplaySeedService.ReplaySeedRequest body) {
        if (body == null || body.matchCode() == null || body.matchCode().isBlank()) {
            return ApiResponse.fail("matchCode required");
        }
        return ApiResponse.ok(replaySeedService.seed(body));
    }

    /**
     * 从 classpath 演示资源灌入 Redis 物化视图 + 赛事库（Germany vs Paraguay）。
     * {@code force=true} 时覆盖已有高危表数据。
     */
    @PostMapping("/replay/demo")
    public ApiResponse<Map<String, Object>> loadDemoReplay(
            @RequestParam(defaultValue = "false") boolean force) {
        return ApiResponse.ok(demoBootstrapService.loadDemo(force));
    }

    /** 停盘 / 开盘：SUSPENDED 时在线试算拒单。 */
    @PostMapping("/matches/{matchCode}/status")
    public ApiResponse<SportsMatchView> setMatchStatus(
            @PathVariable String matchCode,
            @RequestBody Map<String, String> body) {
        String status = body != null ? body.get("status") : null;
        return ApiResponse.ok(matchStatusService.setStatus(matchCode, status));
    }

    /** 层级批量停盘/开盘：overall | sport | league（写入该层下全部赛事 status）。 */
    @GetMapping("/scopes/{scopeType}/{scopeKey}/status")
    public ApiResponse<Map<String, Object>> getScopeTradingStatus(
            @PathVariable String scopeType,
            @PathVariable String scopeKey) {
        return ApiResponse.ok(matchStatusService.scopeStatusSummary(
                LimitScopeType.from(scopeType), scopeKey));
    }

    @PostMapping("/scopes/{scopeType}/{scopeKey}/status")
    public ApiResponse<Map<String, Object>> setScopeTradingStatus(
            @PathVariable String scopeType,
            @PathVariable String scopeKey,
            @RequestBody Map<String, String> body) {
        String status = body != null ? body.get("status") : null;
        return ApiResponse.ok(matchStatusService.setScopeStatus(
                LimitScopeType.from(scopeType), scopeKey, status));
    }

    @GetMapping("/scopes/league/{sportCode}/{leagueCode}/status")
    public ApiResponse<Map<String, Object>> getLeagueTradingStatus(
            @PathVariable String sportCode,
            @PathVariable String leagueCode) {
        return ApiResponse.ok(matchStatusService.scopeStatusSummary(
                LimitScopeType.LEAGUE, ScopeLimitOverride.leagueKey(sportCode, leagueCode)));
    }

    @PostMapping("/scopes/league/{sportCode}/{leagueCode}/status")
    public ApiResponse<Map<String, Object>> setLeagueTradingStatus(
            @PathVariable String sportCode,
            @PathVariable String leagueCode,
            @RequestBody Map<String, String> body) {
        String status = body != null ? body.get("status") : null;
        return ApiResponse.ok(matchStatusService.setScopeStatus(
                LimitScopeType.LEAGUE, ScopeLimitOverride.leagueKey(sportCode, leagueCode), status));
    }

    @GetMapping("/bets")
    public ApiResponse<List<SportsBetLog>> recentBets(
            @RequestParam(required = false) String matchCode,
            @RequestParam(defaultValue = "50") int limit) {
        if (matchCode != null && !matchCode.isBlank()) {
            return ApiResponse.ok(betLogRepository.findByMatch(matchCode, limit));
        }
        return ApiResponse.ok(betLogRepository.findRecent(limit));
    }
}
