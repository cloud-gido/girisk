package com.girisk.sports.service;

import com.girisk.audit.OpsAuditService;
import com.girisk.sports.model.SportsMatch;
import com.girisk.sports.repository.SportsMatchRepository;
import com.girisk.sports.store.ExposureStore;
import com.girisk.sports.store.FixtureLimitOverrideStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理员一键清除赛事测试数据：Redis 物化视图 + ExposureStore + sports_match 行 + 单场覆盖。
 * <p>不触碰 overall/sport/league 层限额与门控配置。
 */
@Service
public class FixtureViewPurgeService {

    private static final Logger log = LoggerFactory.getLogger(FixtureViewPurgeService.class);
    private static final String FIXTURE_VIEW_PREFIX = "girisk:view:fixture:";
    /** 与 Engine Redis 决策计数幂等 SET 对齐（见 DecisionCountIdempotency）。 */
    private static final String FIXTURE_DECISION_IDEMP_PREFIX = "girisk:idem:fixture-decision:";
    private static final String TOP_WORST_LOSS = "girisk:view:top:worstloss";
    private static final String FIXTURE_OVERRIDE_PREFIX = "girisk:override:fixture:";
    private static final String MATCH_GATE_PREFIX = "girisk:gates:scope:match:";

    private final StringRedisTemplate redis;
    private final SportsMatchRepository matchRepository;
    private final ExposureStore exposureStore;
    private final FixtureLimitOverrideStore fixtureLimitOverrideStore;
    private final OpsAuditService audit;
    private final ScopeDutyAuth dutyAuth;

    public FixtureViewPurgeService(
            ObjectProvider<StringRedisTemplate> redis,
            SportsMatchRepository matchRepository,
            ExposureStore exposureStore,
            FixtureLimitOverrideStore fixtureLimitOverrideStore,
            OpsAuditService audit,
            ScopeDutyAuth dutyAuth) {
        this.redis = redis.getIfAvailable();
        this.matchRepository = matchRepository;
        this.exposureStore = exposureStore;
        this.fixtureLimitOverrideStore = fixtureLimitOverrideStore;
        this.audit = audit;
        this.dutyAuth = dutyAuth;
    }

    public Map<String, Object> purgeAllMatches() {
        List<String> codes = collectMatchCodes();
        int redisViews = 0;
        int exposureCleared = 0;
        int overridesCleared = 0;
        int gatesCleared = 0;

        for (String code : codes) {
            if (redis != null) {
                Boolean deleted = redis.delete(FIXTURE_VIEW_PREFIX + code);
                if (Boolean.TRUE.equals(deleted)) {
                    redisViews++;
                }
                redis.delete(FIXTURE_DECISION_IDEMP_PREFIX + code);
                redis.opsForZSet().remove(TOP_WORST_LOSS, code);
                if (Boolean.TRUE.equals(redis.delete(MATCH_GATE_PREFIX + code))) {
                    gatesCleared++;
                }
            }
            try {
                exposureStore.clearMatch(code);
                exposureCleared++;
            } catch (Exception e) {
                log.warn("clearMatch exposure failed code={}: {}", code, e.getMessage());
            }
            try {
                fixtureLimitOverrideStore.delete(code);
                overridesCleared++;
            } catch (Exception e) {
                log.warn("clear fixture override failed code={}: {}", code, e.getMessage());
            }
        }

        // 扫尾：孤儿 Redis 视图 / 覆盖键
        if (redis != null) {
            redisViews += deleteKeys(FIXTURE_VIEW_PREFIX + "*");
            deleteKeys(FIXTURE_DECISION_IDEMP_PREFIX + "*");
            overridesCleared += deleteKeys(FIXTURE_OVERRIDE_PREFIX + "*");
            gatesCleared += deleteKeys(MATCH_GATE_PREFIX + "*");
            redis.delete(TOP_WORST_LOSS);
        }

        int dbDeleted = matchRepository.deleteAll();
        String by = dutyAuth.currentUsername();
        audit.record(
                OpsAuditService.DUTY_FIXTURE_PURGE,
                "一键清除赛事",
                "by=" + by
                        + " matches=" + codes.size()
                        + " redisViews=" + redisViews
                        + " dbDeleted=" + dbDeleted);

        log.warn(
                "Purged all fixtures by={} codes={} redisViews={} dbDeleted={}",
                by, codes.size(), redisViews, dbDeleted);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("matchCodes", codes.size());
        out.put("redisViewsDeleted", redisViews);
        out.put("exposureCleared", exposureCleared);
        out.put("fixtureOverridesCleared", overridesCleared);
        out.put("matchGatesCleared", gatesCleared);
        out.put("sportsMatchDeleted", dbDeleted);
        out.put("by", by);
        return out;
    }

    private List<String> collectMatchCodes() {
        List<String> codes = new ArrayList<>();
        for (SportsMatch m : matchRepository.findAll()) {
            if (m != null && m.matchCode() != null && !m.matchCode().isBlank()) {
                codes.add(m.matchCode().trim());
            }
        }
        if (redis != null) {
            Set<String> keys = redis.keys(FIXTURE_VIEW_PREFIX + "*");
            if (keys != null) {
                for (String key : keys) {
                    String id = key.substring(FIXTURE_VIEW_PREFIX.length()).trim();
                    // 跳过误匹配的子键（若未来在同前缀下挂附属 key）
                    if (id.isEmpty() || id.contains(":")) {
                        continue;
                    }
                    if (!codes.contains(id)) {
                        codes.add(id);
                    }
                }
            }
        }
        return codes;
    }

    private int deleteKeys(String pattern) {
        if (redis == null) {
            return 0;
        }
        Set<String> keys = redis.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        Long n = redis.delete(keys);
        return n == null ? 0 : n.intValue();
    }
}
