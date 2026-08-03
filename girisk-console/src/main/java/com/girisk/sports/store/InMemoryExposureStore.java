package com.girisk.sports.store;

import com.girisk.sports.model.MarketGroupKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "girisk.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryExposureStore implements ExposureStore {

    private final Map<String, BigDecimal> stakes = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> payouts = new ConcurrentHashMap<>();
    private final Map<String, Boolean> orders = new ConcurrentHashMap<>();
    private final Map<String, ReserveRecord> reserves = new ConcurrentHashMap<>();
    private final Map<String, Long> settled = new ConcurrentHashMap<>();

    @Override
    public BigDecimal getStake(MarketGroupKey key, String selection) {
        return stakes.getOrDefault(stakeKey(key, selection), BigDecimal.ZERO);
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
            map.put(sel, payouts.getOrDefault(payoutKey(key, sel), BigDecimal.ZERO));
        }
        // 加上 PENDING 预留返彩
        for (ReserveRecord r : reserves.values()) {
            if (!"PENDING".equals(r.status())) continue;
            if (!r.matchCode().equals(key.matchCode())) continue;
            if (!r.marketType().equals(key.marketType().name())) continue;
            String line = key.line() == null ? "" : key.line();
            String rLine = r.line() == null ? "" : r.line();
            if (!line.equals(rLine)) continue;
            map.merge(r.selection(), r.payoutYuan(), BigDecimal::add);
        }
        return map;
    }

    @Override
    public void addStake(MarketGroupKey key, String selection, BigDecimal amount) {
        stakes.merge(stakeKey(key, selection), amount, BigDecimal::add);
    }

    @Override
    public void addPayout(MarketGroupKey key, String selection, BigDecimal payoutYuan) {
        payouts.merge(payoutKey(key, selection), payoutYuan, BigDecimal::add);
    }

    @Override
    public boolean isOrderProcessed(String orderId) {
        return orders.containsKey(orderId) || reserves.containsKey(orderId);
    }

    @Override
    public void markOrderProcessed(String orderId) {
        orders.put(orderId, Boolean.TRUE);
    }

    @Override
    public BigDecimal getMatchTotalStake(String matchCode) {
        return stakes.entrySet().stream()
                .filter(e -> e.getKey().startsWith(matchCode + ":"))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public void seedGroup(MarketGroupKey key, Map<String, BigDecimal> seed) {
        seed.forEach((sel, amt) -> stakes.put(stakeKey(key, sel), amt));
    }

    @Override
    public void seedGroupPayouts(MarketGroupKey key, Map<String, BigDecimal> seed) {
        seed.forEach((sel, amt) -> payouts.put(payoutKey(key, sel), amt));
    }

    @Override
    public void clearMatch(String matchCode) {
        String prefix = matchCode + ":";
        stakes.keySet().removeIf(k -> k.startsWith(prefix));
        payouts.keySet().removeIf(k -> k.contains(":" + matchCode + ":") || k.startsWith("payout:" + prefix));
    }

    @Override
    public synchronized Optional<String> tryReserve(
            String orderId,
            MarketGroupKey key,
            String selection,
            BigDecimal stakeYuan,
            BigDecimal payoutYuan,
            BigDecimal bMaxPayout,
            long ttlSeconds) {
        if (reserves.containsKey(orderId) || orders.containsKey(orderId)) {
            return Optional.of("订单已处理: " + orderId);
        }
        Map<String, BigDecimal> group = getGroupPayouts(key);
        BigDecimal current = group.getOrDefault(selection, BigDecimal.ZERO);
        // 预占后返彩不得 >= bMax（含等号拒）—— 本单 payout 相对「当前不含本单」的可接额度
        if (payoutYuan.compareTo(bMaxPayout) >= 0) {
            return Optional.of("返彩超过可接上限 bMax=" + bMaxPayout);
        }
        reserves.put(orderId, new ReserveRecord(
                orderId, key.matchCode(), key.marketType().name(), key.line(),
                selection, stakeYuan, payoutYuan, "PENDING"));
        return Optional.empty();
    }

    @Override
    public synchronized boolean confirmReserve(String orderId) {
        ReserveRecord r = reserves.get(orderId);
        if (r == null || !"PENDING".equals(r.status())) {
            return false;
        }
        MarketGroupKey key = MarketGroupKey.of(
                r.matchCode(),
                com.girisk.sports.model.SportsMarketType.valueOf(r.marketType()),
                r.line());
        addStake(key, r.selection(), r.stakeYuan());
        addPayout(key, r.selection(), r.payoutYuan());
        reserves.put(orderId, new ReserveRecord(
                r.orderId(), r.matchCode(), r.marketType(), r.line(),
                r.selection(), r.stakeYuan(), r.payoutYuan(), "CONFIRMED"));
        orders.put(orderId, Boolean.TRUE);
        return true;
    }

    @Override
    public synchronized boolean cancelReserve(String orderId) {
        ReserveRecord r = reserves.remove(orderId);
        if (r == null) {
            return false;
        }
        if ("CONFIRMED".equals(r.status())) {
            MarketGroupKey key = MarketGroupKey.of(
                    r.matchCode(),
                    com.girisk.sports.model.SportsMarketType.valueOf(r.marketType()),
                    r.line());
            stakes.merge(stakeKey(key, r.selection()), r.stakeYuan().negate(), BigDecimal::add);
            payouts.merge(payoutKey(key, r.selection()), r.payoutYuan().negate(), BigDecimal::add);
        }
        orders.remove(orderId);
        return true;
    }

    @Override
    public Optional<ReserveRecord> getReserve(String orderId) {
        return Optional.ofNullable(reserves.get(orderId));
    }

    @Override
    public void markSettled(String orderId, long settlePnlCents) {
        settled.put(orderId, settlePnlCents);
        orders.put(orderId, Boolean.TRUE);
    }

    @Override
    public Optional<Long> getSettledPnl(String orderId) {
        return Optional.ofNullable(settled.get(orderId));
    }

    private static String stakeKey(MarketGroupKey key, String selection) {
        return key.redisKey() + ":" + selection;
    }

    private static String payoutKey(MarketGroupKey key, String selection) {
        return "payout:" + key.redisKey() + ":" + selection;
    }
}
