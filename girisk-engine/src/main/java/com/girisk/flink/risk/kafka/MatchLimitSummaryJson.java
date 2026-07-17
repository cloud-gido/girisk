package com.girisk.flink.risk.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.limit.ExposureLimitGate;
import com.girisk.flink.risk.limit.LimitSelectionResolver;
import com.girisk.flink.risk.limit.LimitSelectionResolver.ResolvedOutcome;
import com.girisk.flink.risk.limit.MarketStakeAggregator;
import com.girisk.flink.risk.limit.MarketStakeAggregator.MarketGroupLimit;
import com.girisk.flink.risk.limit.MarketStakeAggregator.OutcomeLimitRow;
import com.girisk.flink.risk.model.EnrichedFootballOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 场次等比例限额快照 JSON（schemaVersion=4）。
 *
 * <p>v4：金额口径为<strong>返彩（投注金额 × 赔率）</strong>，含冷启动种子；限额恒开（先限额后敞口），
 * 新增 {@code rejectReason}（LIMIT / EXPOSURE / NONE）。
 */
public final class MatchLimitSummaryJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MatchLimitSummaryJson() {}

    public static String limitSnapshotJson(
            EnrichedFootballOrder trigger,
            String matchKey,
            int windowOrderCount,
            boolean duplicateIgnored,
            double delta,
            double seedPayoutYuan,
            List<FootballSportsOrder> priorConfirmedOrders,
            List<FootballSportsOrder> trialOrdersIncludingTrigger,
            String rejectReason,
            double maxExposureYuan,
            double maxWorstLossYuan,
            boolean postFeedbackMode,
            long publishedAtMs) {
        List<MarketGroupLimit> priorGroups =
                MarketStakeAggregator.aggregate(
                        priorConfirmedOrders, delta, seedPayoutYuan, trigger.order);
        List<MarketGroupLimit> includingGroups =
                MarketStakeAggregator.aggregate(
                        trialOrdersIncludingTrigger, delta, seedPayoutYuan, trigger.order);

        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", 4);
        root.put("eventId", RiskKafkaMessageIds.newEventId());
        root.put("upstreamEventId", RiskKafkaMessageIds.upstreamEventId(trigger.order));
        root.put("operatorId", trigger.order.operatorId);
        root.put("fixtureId", nz(trigger.order.fixtureId));
        root.put("matchKey", matchKey);
        root.put("league", nz(trigger.order.league));
        root.put("homeTeam", nz(trigger.order.homeTeam));
        root.put("awayTeam", nz(trigger.order.awayTeam));
        root.put("kickoffTime", nz(trigger.order.kickoffTime));
        root.put("triggerOrderId", nz(trigger.order.orderId));
        root.set("triggerOrder", TriggerOrderJson.nested(trigger));
        root.put("eventTimeMs", trigger.orderTimeMs);
        root.put("windowOrderCount", windowOrderCount);
        root.put("duplicateIgnored", duplicateIgnored);
        root.put("basis", "payout");
        root.put("delta", delta);
        root.put("initialSeedPayoutYuan", seedPayoutYuan);
        root.put("rejectReason", rejectReason == null ? "NONE" : rejectReason);
        root.put("maxExposureYuan", maxExposureYuan);
        if (maxWorstLossYuan < ExposureLimitGate.WORST_LOSS_DISABLED) {
            root.put("worstLossThresholdYuan", Math.abs(maxWorstLossYuan));
        }
        root.put("publishedAtMs", publishedAtMs);
        root.put("limitBasis", postFeedbackMode ? "postConfirmedPrior" : "priorToTrigger");
        if (postFeedbackMode) {
            root.put("confirmedOrderSource", "girisk.trading.order.risk-check.post.v1");
        }

        root.set("marketGroups", marketGroupsArray(priorGroups));
        root.set("marketGroupsIncludingTrigger", marketGroupsArray(includingGroups));
        root.set("triggerSelection", triggerSelectionNode(trigger.order, priorGroups));
        return root.toString();
    }

    private static ArrayNode marketGroupsArray(List<MarketGroupLimit> groups) {
        ArrayNode marketGroups = MAPPER.createArrayNode();
        for (MarketGroupLimit g : groups) {
            marketGroups.add(marketGroupNode(g));
        }
        return marketGroups;
    }

    private static ObjectNode marketGroupNode(MarketGroupLimit g) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("marketType", g.marketTypeCode);
        n.put("marketLabel", g.marketLabel);
        n.put("line", g.line);
        n.put("groupKey", g.groupKey);
        n.put("groupTotalStake", g.groupTotalStake);

        ArrayNode outcomes = MAPPER.createArrayNode();
        for (OutcomeLimitRow row : g.rows) {
            outcomes.add(outcomeNode(row));
        }
        n.set("outcomes", outcomes);
        return n;
    }

    private static ObjectNode outcomeNode(OutcomeLimitRow row) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("selection", row.selection);
        n.put("selectionLabel", row.selectionLabel);
        n.put("stake", row.stake);
        n.put("targetAmount", row.targetAmount);
        n.put("maxAllowedAmount", row.maxAllowedAmount);
        n.put("acceptMax", row.acceptMax);
        n.put("overLimit", row.overLimit);
        return n;
    }

    static ObjectNode triggerSelectionNode(
            FootballSportsOrder triggerOrder, List<MarketGroupLimit> priorGroups) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("proposedStake", triggerOrder.stakeYuan);
        BigDecimal proposedPayout = MarketStakeAggregator.payoutYuan(triggerOrder);
        n.put("proposedPayout", proposedPayout);

        Optional<ResolvedOutcome> resolved = LimitSelectionResolver.resolve(triggerOrder);
        if (!resolved.isPresent()) {
            n.put("resolved", false);
            n.put("shouldReject", false);
            return n;
        }

        ResolvedOutcome r = resolved.get();
        String groupKey = LimitSelectionResolver.groupKey(r.marketType, r.line);
        OutcomeLimitRow row = findOutcome(priorGroups, groupKey, r.selectionKey);

        n.put("resolved", true);
        n.put("marketType", r.marketType.name());
        n.put("marketLabel", r.marketType.label());
        n.put("line", r.line == null ? "" : r.line);
        n.put("groupKey", groupKey);
        n.put("selection", r.selectionKey);

        if (row == null) {
            n.put("selectionLabel", r.selectionKey);
            n.put("stakeBefore", 0);
            n.put("acceptMaxBefore", 0);
            n.put("overLimitBefore", false);
            n.put("shouldReject", false);
            return n;
        }

        n.put("selectionLabel", row.selectionLabel);
        n.put("stakeBefore", row.stake);
        n.put("targetAmountBefore", row.targetAmount);
        n.put("maxAllowedAmountBefore", row.maxAllowedAmount);
        n.put("acceptMaxBefore", row.acceptMax);
        n.put("overLimitBefore", row.overLimit);

        n.put("shouldReject", proposedPayout.compareTo(row.acceptMax) >= 0);
        return n;
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

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
