package com.girisk.sports.store;

import com.girisk.sports.model.MarketGroupKey;
import com.girisk.sports.model.SportsMarketType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "girisk.redis.enabled", havingValue = "true")
public class RedisExposureStore implements ExposureStore {

    private static final String STAKE_PREFIX = "sports:stake:";
    private static final String PAYOUT_PREFIX = "sports:payout:";
    private static final String ORDER_PREFIX = "sports:order:";
    private static final String RESERVE_PREFIX = "risk:reserve:";
    private static final String SETTLED_PREFIX = "risk:settled:";
    private static final String MATCH_KEYS_PREFIX = "sports:matchkeys:";

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> reserveScript;

    public RedisExposureStore(StringRedisTemplate redis) {
        this.redis = redis;
        this.reserveScript = new DefaultRedisScript<>();
        this.reserveScript.setResultType(Long.class);
        this.reserveScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/reserve.lua")));
    }

    @Override
    public BigDecimal getStake(MarketGroupKey key, String selection) {
        String val = redis.opsForValue().get(stakeKey(key, selection));
        return val != null ? new BigDecimal(val) : BigDecimal.ZERO;
    }

    @Override
    public Map<String, BigDecimal> getGroupStakes(MarketGroupKey key) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (String sel : key.marketType().selections()) {
            map.put(sel, getStake(key, sel));
        }
        return map;
    }

    @Override
    public Map<String, BigDecimal> getGroupPayouts(MarketGroupKey key) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (String sel : key.marketType().selections()) {
            String val = redis.opsForValue().get(payoutKey(key, sel));
            map.put(sel, val != null ? new BigDecimal(val) : BigDecimal.ZERO);
        }
        return map;
    }

    @Override
    public void addStake(MarketGroupKey key, String selection, BigDecimal amount) {
        String k = stakeKey(key, selection);
        redis.opsForValue().increment(k, amount.doubleValue());
        redis.expire(k, 7, TimeUnit.DAYS);
        redis.opsForSet().add(MATCH_KEYS_PREFIX + key.matchCode(), k);
    }

    @Override
    public void addPayout(MarketGroupKey key, String selection, BigDecimal payoutYuan) {
        String k = payoutKey(key, selection);
        redis.opsForValue().increment(k, payoutYuan.doubleValue());
        redis.expire(k, 7, TimeUnit.DAYS);
        redis.opsForSet().add(MATCH_KEYS_PREFIX + key.matchCode(), k);
    }

    @Override
    public boolean isOrderProcessed(String orderId) {
        return Boolean.TRUE.equals(redis.hasKey(ORDER_PREFIX + orderId))
                || Boolean.TRUE.equals(redis.hasKey(RESERVE_PREFIX + orderId));
    }

    @Override
    public void markOrderProcessed(String orderId) {
        redis.opsForValue().set(ORDER_PREFIX + orderId, "1", 7, TimeUnit.DAYS);
    }

    @Override
    public BigDecimal getMatchTotalStake(String matchCode) {
        Set<String> keys = redis.opsForSet().members(MATCH_KEYS_PREFIX + matchCode);
        if (keys == null || keys.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (String key : keys) {
            if (!key.startsWith(STAKE_PREFIX)) continue;
            String val = redis.opsForValue().get(key);
            if (val != null) total = total.add(new BigDecimal(val));
        }
        return total;
    }

    @Override
    public void seedGroup(MarketGroupKey key, Map<String, BigDecimal> seed) {
        seed.forEach((sel, amt) -> {
            redis.opsForValue().set(stakeKey(key, sel), amt.toPlainString());
            redis.opsForSet().add(MATCH_KEYS_PREFIX + key.matchCode(), stakeKey(key, sel));
        });
    }

    @Override
    public void seedGroupPayouts(MarketGroupKey key, Map<String, BigDecimal> seed) {
        seed.forEach((sel, amt) -> {
            redis.opsForValue().set(payoutKey(key, sel), amt.toPlainString());
            redis.opsForSet().add(MATCH_KEYS_PREFIX + key.matchCode(), payoutKey(key, sel));
        });
    }

    @Override
    public void clearMatch(String matchCode) {
        Set<String> keys = redis.opsForSet().members(MATCH_KEYS_PREFIX + matchCode);
        if (keys != null) {
            for (String key : keys) {
                redis.delete(key);
            }
        }
        redis.delete(MATCH_KEYS_PREFIX + matchCode);
    }

    @Override
    public Optional<String> tryReserve(
            String orderId,
            MarketGroupKey key,
            String selection,
            BigDecimal stakeYuan,
            BigDecimal payoutYuan,
            BigDecimal bMaxPayout,
            long ttlSeconds) {
        // KEYS: reserveKey, payoutKey
        // ARGV: orderId, payout, bMax, ttl, match, market, line, selection, stake
        Long code = redis.execute(
                reserveScript,
                List.of(RESERVE_PREFIX + orderId, payoutKey(key, selection)),
                orderId,
                payoutYuan.toPlainString(),
                bMaxPayout.toPlainString(),
                String.valueOf(ttlSeconds),
                key.matchCode(),
                key.marketType().name(),
                key.line() == null ? "" : key.line(),
                selection,
                stakeYuan.toPlainString());
        if (code == null) return Optional.of("Redis reserve 失败");
        return switch (code.intValue()) {
            case 0 -> Optional.empty();
            case 1 -> Optional.of("订单已处理: " + orderId);
            case 2 -> Optional.of("返彩超过可接上限 bMax=" + bMaxPayout);
            default -> Optional.of("预占失败 code=" + code);
        };
    }

    @Override
    public boolean confirmReserve(String orderId) {
        Optional<ReserveRecord> opt = getReserve(orderId);
        if (opt.isEmpty() || !"PENDING".equals(opt.get().status())) {
            return false;
        }
        ReserveRecord r = opt.get();
        MarketGroupKey key = MarketGroupKey.of(
                r.matchCode(), SportsMarketType.valueOf(r.marketType()), r.line());
        // payout 已在 tryReserve Lua 中累加，此处只确认 stake
        addStake(key, r.selection(), r.stakeYuan());
        redis.opsForHash().put(RESERVE_PREFIX + orderId, "status", "CONFIRMED");
        redis.persist(RESERVE_PREFIX + orderId);
        markOrderProcessed(orderId);
        return true;
    }

    @Override
    public boolean cancelReserve(String orderId) {
        Optional<ReserveRecord> opt = getReserve(orderId);
        if (opt.isEmpty()) {
            return false;
        }
        ReserveRecord r = opt.get();
        MarketGroupKey key = MarketGroupKey.of(
                r.matchCode(), SportsMarketType.valueOf(r.marketType()), r.line());
        if ("PENDING".equals(r.status())) {
            addPayout(key, r.selection(), r.payoutYuan().negate());
        } else if ("CONFIRMED".equals(r.status())) {
            addStake(key, r.selection(), r.stakeYuan().negate());
            addPayout(key, r.selection(), r.payoutYuan().negate());
        }
        redis.delete(RESERVE_PREFIX + orderId);
        redis.delete(ORDER_PREFIX + orderId);
        return true;
    }

    @Override
    public Optional<ReserveRecord> getReserve(String orderId) {
        Map<Object, Object> map = redis.opsForHash().entries(RESERVE_PREFIX + orderId);
        if (map == null || map.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ReserveRecord(
                str(map.get("orderId")),
                str(map.get("matchCode")),
                str(map.get("marketType")),
                str(map.get("line")),
                str(map.get("selection")),
                new BigDecimal(str(map.get("stakeYuan"))),
                new BigDecimal(str(map.get("payoutYuan"))),
                str(map.get("status"))));
    }

    @Override
    public void markSettled(String orderId, long settlePnlCents) {
        redis.opsForValue().set(SETTLED_PREFIX + orderId, String.valueOf(settlePnlCents), 30, TimeUnit.DAYS);
        markOrderProcessed(orderId);
    }

    @Override
    public Optional<Long> getSettledPnl(String orderId) {
        String val = redis.opsForValue().get(SETTLED_PREFIX + orderId);
        if (val == null || val.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(val));
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String stakeKey(MarketGroupKey key, String selection) {
        return STAKE_PREFIX + key.redisKey() + ":" + selection;
    }

    private static String payoutKey(MarketGroupKey key, String selection) {
        return PAYOUT_PREFIX + key.redisKey() + ":" + selection;
    }
}
