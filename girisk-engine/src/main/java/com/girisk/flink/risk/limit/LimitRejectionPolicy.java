package com.girisk.flink.risk.limit;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.limit.MarketStakeAggregator.MarketGroupLimit;
import com.girisk.flink.risk.limit.MarketStakeAggregator.OutcomeLimitRow;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Gate 1 等比例限额（恒开）：本笔返彩（投注金额 × 赔率）{@code >=} 该盘口 {@code b_max} 时拒单。
 *
 * <p>{@code b_max} 基于 prior 窗口 + 每盘口虚拟种子；trigger 所属分组即使窗口为空也会建组（冷启动）。
 */
public final class LimitRejectionPolicy {

    private LimitRejectionPolicy() {}

    public static boolean shouldReject(
            FootballSportsOrder triggerOrder,
            List<FootballSportsOrder> priorOrders,
            double delta,
            double seedYuan) {
        Optional<LimitSelectionResolver.ResolvedOutcome> resolved =
                LimitSelectionResolver.resolve(triggerOrder);
        if (!resolved.isPresent()) {
            return false;
        }
        LimitSelectionResolver.ResolvedOutcome r = resolved.get();
        String groupKey = LimitSelectionResolver.groupKey(r.marketType, r.line);
        List<MarketGroupLimit> priorGroups =
                MarketStakeAggregator.aggregate(priorOrders, delta, seedYuan, triggerOrder);
        OutcomeLimitRow row = findOutcome(priorGroups, groupKey, r.selectionKey);
        if (row == null) {
            return false;
        }
        return MarketStakeAggregator.payoutYuan(triggerOrder).compareTo(row.acceptMax) >= 0;
    }

    private static OutcomeLimitRow findOutcome(
            List<MarketGroupLimit> groups, String groupKey, String selection) {
        for (MarketGroupLimit g : groups) {
            if (!groupKey.equals(g.groupKey)) {
                continue;
            }
            for (OutcomeLimitRow row : g.rows) {
                if (selection.equals(row.selection)) {
                    return row;
                }
            }
        }
        return null;
    }
}
