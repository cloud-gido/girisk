package com.girisk.flink;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.configcenter.model.RiskFixtureView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads Flink / local-replay Redis materialised views for the exposure dashboard. */
@Component
public class RedisFixtureViewReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redis;

    public RedisFixtureViewReader(ObjectProvider<StringRedisTemplate> redis) {
        this.redis = redis.getIfAvailable();
    }

    public boolean available() {
        return redis != null;
    }

    public List<RiskFixtureView> topByWorstLoss(int limit) {
        if (redis == null) {
            return List.of();
        }
        Set<String> ids = redis.opsForZSet().reverseRange("girisk:view:top:worstloss", 0, Math.max(0, limit - 1));
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<RiskFixtureView> out = new ArrayList<>();
        long i = 1;
        for (String fixtureId : ids) {
            RiskFixtureView v = readOne(fixtureId, i++);
            if (v != null) {
                out.add(v);
            }
        }
        return out;
    }

    public RiskFixtureView findByFixtureId(String fixtureId) {
        if (redis == null || fixtureId == null || fixtureId.isBlank()) {
            return null;
        }
        return readOne(fixtureId, 0L);
    }

    public List<RiskFixtureView> listAll(int maxScan) {
        if (redis == null) {
            return List.of();
        }
        Set<String> keys = redis.keys("girisk:view:fixture:*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<String> sorted = new ArrayList<>(keys);
        Collections.sort(sorted);
        List<RiskFixtureView> out = new ArrayList<>();
        long i = 1;
        for (String key : sorted) {
            if (out.size() >= maxScan) {
                break;
            }
            String fixtureId = key.substring("girisk:view:fixture:".length());
            RiskFixtureView v = readOne(fixtureId, i++);
            if (v != null) {
                out.add(v);
            }
        }
        return out;
    }

    private RiskFixtureView readOne(String fixtureId, long syntheticId) {
        Map<Object, Object> hash = redis.opsForHash().entries("girisk:view:fixture:" + fixtureId);
        if (hash == null || hash.isEmpty()) {
            return null;
        }
        long worst = parseLong(hash.get("worstLossCents"));
        int confirmed = (int) parseLong(hash.get("confirmedOrders"));
        long updatedMs = parseLong(hash.get("updatedAt"));
        LocalDateTime updated = updatedMs > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(updatedMs), ZoneId.systemDefault())
                : LocalDateTime.now();
        String level = worst >= 500_00 ? "HIGH" : worst >= 100_00 ? "MEDIUM" : "LOW";
        Map<String, Object> replayStats = parseReplayStats(str(hash.get("replayStats"), null));
        List<Map<String, Object>> marketGroups = parseMarketGroups(str(hash.get("marketGroups"), null));
        Double limitDelta = parseDouble(hash.get("limitDelta"));
        Double seed = parseDouble(hash.get("initialSeedPayoutYuan"));
        Long marketGroupsUpdatedAt = parseLongObj(hash.get("marketGroupsUpdatedAt"));
        return new RiskFixtureView(
                syntheticId,
                fixtureId,
                str(hash.get("homeTeam"), "-"),
                str(hash.get("awayTeam"), "-"),
                str(hash.get("operatorId"), null),
                confirmed,
                (int) parseLong(hash.get("pendingReserved")),
                worst,
                str(hash.get("worstScore"), null),
                str(hash.get("liveScore"), null),
                str(hash.get("rawSnapshot"), null),
                level,
                updated,
                replayStats,
                marketGroups,
                limitDelta,
                seed,
                marketGroupsUpdatedAt);
    }

    private static Map<String, Object> parseReplayStats(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Map<String, Object>> parseMarketGroups(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o, String def) {
        return o == null ? def : o.toString();
    }

    private static long parseLong(Object o) {
        if (o == null) {
            return 0;
        }
        try {
            return Long.parseLong(o.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private static Long parseLongObj(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Long.parseLong(o.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static Double parseDouble(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Double.parseDouble(o.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
