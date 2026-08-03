package com.girisk.sports.store;

import com.girisk.sports.model.MarketGroupKey;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

public interface ExposureStore {

    BigDecimal getStake(MarketGroupKey key, String selection);

    Map<String, BigDecimal> getGroupStakes(MarketGroupKey key);

    /** 盘口累计返彩（payout = stake×odds）。 */
    Map<String, BigDecimal> getGroupPayouts(MarketGroupKey key);

    void addStake(MarketGroupKey key, String selection, BigDecimal amount);

    void addPayout(MarketGroupKey key, String selection, BigDecimal payoutYuan);

    boolean isOrderProcessed(String orderId);

    void markOrderProcessed(String orderId);

    BigDecimal getMatchTotalStake(String matchCode);

    void seedGroup(MarketGroupKey key, Map<String, BigDecimal> stakes);

    /** 覆盖写入盘口累计返彩（演示回放灌数用）。 */
    default void seedGroupPayouts(MarketGroupKey key, Map<String, BigDecimal> payouts) {
        payouts.forEach((sel, amt) -> addPayout(key, sel, amt));
    }

    /** 清除某场次下所有 stake/payout 键（灌数前重置）。 */
    void clearMatch(String matchCode);

    /**
     * 原子预占：校验后写入 PENDING 预留。
     * @return empty 表示成功；否则为失败原因
     */
    Optional<String> tryReserve(
            String orderId,
            MarketGroupKey key,
            String selection,
            BigDecimal stakeYuan,
            BigDecimal payoutYuan,
            BigDecimal bMaxPayout,
            long ttlSeconds);

    /** 预占转正式持仓。 */
    boolean confirmReserve(String orderId);

    /** 释放预占或正式持仓。 */
    boolean cancelReserve(String orderId);

    Optional<ReserveRecord> getReserve(String orderId);

    /** 标记订单已结算（持久化，跨重启）。 */
    void markSettled(String orderId, long settlePnlCents);

    Optional<Long> getSettledPnl(String orderId);

    record ReserveRecord(
            String orderId,
            String matchCode,
            String marketType,
            String line,
            String selection,
            BigDecimal stakeYuan,
            BigDecimal payoutYuan,
            String status
    ) {}
}
