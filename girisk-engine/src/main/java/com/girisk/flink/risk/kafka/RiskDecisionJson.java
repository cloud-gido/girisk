package com.girisk.flink.risk.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.girisk.common.decision.RiskDecisionCodes;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.grid.MatchExposureAggregator;
import com.girisk.flink.risk.grid.MatchExposureAggregator.ExposureSummary;
import com.girisk.flink.risk.grid.MatchExposureAggregator.ScenarioExposure;
import com.girisk.flink.risk.limit.LimitSelectionResolver;
import com.girisk.flink.risk.limit.LimitSelectionResolver.ResolvedOutcome;
import com.girisk.flink.risk.limit.MarketStakeAggregator;
import com.girisk.flink.risk.limit.MarketStakeAggregator.MarketGroupLimit;
import com.girisk.flink.risk.limit.MarketStakeAggregator.OutcomeLimitRow;
import com.girisk.flink.risk.limit.MatchTriggerAcceptance;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import com.girisk.flink.risk.settlement.BetMarketFamily;
import com.girisk.flink.risk.settlement.PlayTypeRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Builds {@code girisk.decision.v1} — 审计自包含，含产品限额/敞口明细字段（{@code productAudit}）。
 */
public final class RiskDecisionJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String SCHEMA_VERSION = "1";

    /** 与 ProportionalLimitCalculator 一致。 */
    public static final String LIMIT_FORMULA =
            "b_max=((1+δ)·w·S_total-S_i)/(1-(1+δ)·w)，w=1/n；金额口径=投注额×赔率（返彩）";

    private RiskDecisionJson() {}

    public static String fromAcceptance(
            EnrichedFootballOrder trigger,
            MatchTriggerAcceptance acceptance,
            double limitDelta,
            double seedPayoutYuan,
            double maxWorstLossYuan,
            long publishedAtMs,
            String engineBuild) {
        return fromAcceptance(
                trigger,
                acceptance,
                null,
                limitDelta,
                seedPayoutYuan,
                maxWorstLossYuan,
                publishedAtMs,
                engineBuild);
    }

    /**
     * @param noRiskExposure 全部已见订单（含拒单）的对照敞口；写入 featureSnapshot.noRisk*
     */
    public static String fromAcceptance(
            EnrichedFootballOrder trigger,
            MatchTriggerAcceptance acceptance,
            ExposureSummary noRiskExposure,
            double limitDelta,
            double seedPayoutYuan,
            double maxWorstLossYuan,
            long publishedAtMs,
            String engineBuild) {
        FootballSportsOrder order = trigger.order;
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("decisionTimeMs", publishedAtMs);
        root.put("orderId", nullToEmpty(order.orderId));
        root.put("fixtureId", nullToEmpty(order.fixtureId));
        root.put("operatorId", String.valueOf(order.operatorId));
        root.put("userId", nullToEmpty(order.userId));
        root.put("traceId", nullToEmpty(order.eventId));
        root.put("engineBuild", engineBuild == null ? "girisk-engine" : engineBuild);

        long stakeCents = order.stakeCents();
        String oddsStr =
                BigDecimal.valueOf(order.odds).setScale(3, RoundingMode.HALF_UP).toPlainString();
        long payoutCents =
                BigDecimal.valueOf(stakeCents)
                        .multiply(BigDecimal.valueOf(order.odds))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValue();
        root.put("stakeCents", stakeCents);
        root.put("odds", oddsStr);
        root.put("payoutCents", payoutCents);

        BetMarketFamily family = PlayTypeRegistry.resolve(order);
        ObjectNode market = root.putObject("market");
        market.put("playType", nullToEmpty(order.playType));
        market.put("playTypeResolved", family.name());
        market.put("parlayType", nullToEmpty(order.parlayType));
        market.put("line", nullToEmpty(order.handicapText));
        market.put("selection", nullToEmpty(order.selection));
        market.put("league", nullToEmpty(order.league));
        market.put("homeTeam", nullToEmpty(order.homeTeam));
        market.put("awayTeam", nullToEmpty(order.awayTeam));

        String decision;
        if (acceptance.duplicateIgnored) {
            decision = RiskDecisionCodes.PASS;
        } else if (acceptance.shouldReject) {
            decision = RiskDecisionCodes.REJECT;
        } else {
            decision = RiskDecisionCodes.PASS;
        }
        root.put("decision", decision);

        ArrayNode reasons = root.putArray("reasons");
        if (acceptance.tradingRejected) {
            ObjectNode r = reasons.addObject();
            r.put("ruleId", "R_TRADING_SUSPENDED");
            r.put("stage", "GATE_TRADING");
            r.put("action", RiskDecisionCodes.REJECT);
            r.put("message", "总开关关闭（停盘）");
        }
        if (acceptance.maxBetRejected) {
            ObjectNode r = reasons.addObject();
            r.put("ruleId", "R_LIMIT_MAX_BET");
            r.put("stage", "GATE0_MAX_BET");
            r.put("action", RiskDecisionCodes.REJECT);
            r.put("message", "单注返彩超过上限");
        } else if (acceptance.limitRejected) {
            ObjectNode r = reasons.addObject();
            r.put("ruleId", "R_LIMIT_PROPORTIONAL");
            r.put("stage", "GATE1_LIMIT");
            r.put("action", RiskDecisionCodes.REJECT);
            r.put("message", "等比例限额拒单");
        }
        if (acceptance.exposureRejected) {
            ObjectNode r = reasons.addObject();
            r.put("ruleId", "R_EXPOSURE_WORST_LOSS");
            r.put("stage", "GATE2_EXPOSURE");
            r.put("action", RiskDecisionCodes.REJECT);
            r.put("message", "比分矩阵最差敞口超阈");
        }
        if (reasons.isEmpty()) {
            ObjectNode r = reasons.addObject();
            r.put("ruleId", acceptance.duplicateIgnored ? "R_DUPLICATE" : "R_ACCEPT");
            r.put("stage", "GATE");
            r.put("action", RiskDecisionCodes.PASS);
            r.put(
                    "message",
                    acceptance.duplicateIgnored ? "重复订单忽略" : "限额与敞口均通过");
        }

        List<FootballSportsOrder> priorForLimit = acceptance.confirmedOrders;
        List<MarketGroupLimit> priorGroups =
                MarketStakeAggregator.aggregate(
                        priorForLimit, limitDelta, seedPayoutYuan, order);
        List<MarketGroupLimit> afterGroups =
                acceptance.persistTrigger()
                        ? MarketStakeAggregator.aggregate(
                                acceptance.acceptedOrders, limitDelta, seedPayoutYuan, order)
                        : priorGroups;
        ObjectNode triggerSel =
                MatchLimitSummaryJson.triggerSelectionNode(order, priorGroups);

        ObjectNode evidence = root.putObject("evidence");
        evidence.put("rejectReason", acceptance.rejectReason.name());
        evidence.put("shouldReject", acceptance.shouldReject);
        evidence.put("tradingRejected", acceptance.tradingRejected);
        evidence.put("maxBetRejected", acceptance.maxBetRejected);
        evidence.put("limitRejected", acceptance.limitRejected);
        evidence.put("exposureRejected", acceptance.exposureRejected);
        evidence.put("duplicateIgnored", acceptance.duplicateIgnored);
        evidence.put("limitDelta", limitDelta);
        evidence.put("seedPayoutYuan", seedPayoutYuan);
        evidence.put("maxWorstLossYuan", maxWorstLossYuan);
        evidence.put(
                "trialWorstLossYuan",
                centsToYuanAbs(
                        acceptance.trialExposure == null
                                ? 0L
                                : acceptance.trialExposure.maxBookmakerLossCents));
        evidence.put("confirmedOrderCount", acceptance.confirmedOrders.size());
        evidence.put("pendingAware", acceptance.postFeedbackMode);
        evidence.put(
                "triggerPayoutYuan",
                MarketStakeAggregator.payoutYuan(order).toPlainString());
        evidence.set("gate1TriggerSelection", triggerSel);

        ExposureSummary beforeExp = acceptance.confirmedExposure;
        ExposureSummary trialExp = acceptance.trialExposure;
        // 实际接收后：接单则与试探窗口一致，拒单/重复则与接收前一致
        ExposureSummary afterExp = acceptance.persistTrigger() ? trialExp : beforeExp;

        ObjectNode feature = root.putObject("featureSnapshot");
        feature.put("confirmedOrders", acceptance.confirmedOrders.size());
        feature.put("pendingAware", acceptance.postFeedbackMode);
        feature.put("duplicateIgnored", acceptance.duplicateIgnored);
        feature.put("processingSeq", acceptance.confirmedOrders.size() + 1);
        putExposureBlock(feature, "beforeAccept", beforeExp);
        putExposureBlock(feature, "trialAfterAccept", trialExp);
        putExposureBlock(feature, "afterActual", afterExp);
        feature.put("maxWorstLossYuan", maxWorstLossYuan);
        feature.put("seedPayoutYuan", seedPayoutYuan);
        feature.put("limitDelta", limitDelta);
        if (noRiskExposure != null) {
            ScenarioExposure noRiskWorst = MatchExposureAggregator.worstScenario(noRiskExposure);
            if (noRiskWorst != null) {
                feature.put("noRiskWorstPnlYuan", noRiskWorst.bookmakerPnlCents / 100.0);
                feature.put("noRiskWorstScore", noRiskWorst.scenario.scoreLabel());
            }
        }
        // 兼容旧字段
        ScenarioExposure trialWorst = MatchExposureAggregator.worstScenario(trialExp);
        feature.put(
                "worstLossCents",
                trialExp == null ? 0L : -Math.abs(trialExp.maxBookmakerLossCents));
        feature.put("worstScore", trialWorst == null ? "" : trialWorst.scenario.scoreLabel());

        ObjectNode versions = root.putObject("versions");
        versions.putNull("configEpoch");
        versions.put("paramSetVersion", "embedded");
        versions.put("ruleSetVersion", "embedded");
        versions.put("engineBuild", engineBuild == null ? "girisk-engine" : engineBuild);

        root.put(
                "reason",
                acceptance.shouldReject
                        ? ("拒单:" + acceptance.rejectReason.name())
                        : acceptance.duplicateIgnored ? "重复订单忽略" : "限额与敞口均通过");

        root.set(
                "productAudit",
                buildProductAudit(
                        order,
                        family,
                        acceptance,
                        limitDelta,
                        seedPayoutYuan,
                        maxWorstLossYuan,
                        priorGroups,
                        afterGroups,
                        triggerSel,
                        beforeExp,
                        trialExp,
                        afterExp,
                        decision));

        return root.toString();
    }

    private static ObjectNode buildProductAudit(
            FootballSportsOrder order,
            BetMarketFamily family,
            MatchTriggerAcceptance acceptance,
            double limitDelta,
            double seedPayoutYuan,
            double maxWorstLossYuan,
            List<MarketGroupLimit> priorGroups,
            List<MarketGroupLimit> afterGroups,
            ObjectNode triggerSel,
            ExposureSummary beforeExp,
            ExposureSummary trialExp,
            ExposureSummary afterExp,
            String decision) {
        ObjectNode p = MAPPER.createObjectNode();
        BigDecimal occupation = MarketStakeAggregator.payoutYuan(order);

        p.put("处理顺序", acceptance.confirmedOrders.size() + 1);
        p.put("比赛", matchLabel(order));
        p.put("订单ID", nullToEmpty(order.orderId));
        p.put("玩法", nullToEmpty(order.playType));
        p.put("玩法识别", family.name());
        p.put("限额分组", text(triggerSel, "groupKey"));
        p.put("当前盘口", text(triggerSel, "selectionLabel", text(triggerSel, "selection")));
        p.put("投注项", nullToEmpty(order.selection));
        p.put("盘口", nullToEmpty(order.handicapText));
        putDecimal(p, "投注金额", yuanFromCents(order.stakeCents()));
        putDecimal(
                p,
                "赔率",
                BigDecimal.valueOf(order.odds).setScale(3, RoundingMode.HALF_UP));
        putDecimal(p, "限额占用金额（投注金额×赔率）", occupation);
        p.put("初始已投注金额（每个盘口，投注额×赔率口径）", seedPayoutYuan);
        p.put("容忍偏差delta", limitDelta);

        boolean resolved = triggerSel.path("resolved").asBoolean(false);
        Optional<ResolvedOutcome> resolvedOpt = LimitSelectionResolver.resolve(order);
        MarketGroupLimit priorGroup = null;
        if (resolvedOpt.isPresent()) {
            ResolvedOutcome r = resolvedOpt.get();
            priorGroup =
                    findGroup(
                            priorGroups,
                            LimitSelectionResolver.groupKey(r.marketType, r.line));
        }

        if (resolved && priorGroup != null) {
            putDecimal(p, "判断前组内已投注金额（含初始，投注额×赔率口径）", priorGroup.groupTotalStake);
            putDecimal(
                    p, "判断前分组总已投注金额（含初始，投注额×赔率口径）", priorGroup.groupTotalStake);
            putDecimal(
                    p,
                    "判断前当前盘口已投注金额（含初始，投注额×赔率口径）",
                    decimalOrZero(triggerSel, "stakeBefore"));
            putDecimal(p, "目标金额", decimalOrNull(triggerSel, "targetAmountBefore"));
            putDecimal(p, "最大允许金额", decimalOrNull(triggerSel, "maxAllowedAmountBefore"));
            putDecimal(p, "当前盘口可投注金额", decimalOrNull(triggerSel, "acceptMaxBefore"));
        } else {
            p.putNull("判断前组内已投注金额（含初始，投注额×赔率口径）");
            p.putNull("判断前分组总已投注金额（含初始，投注额×赔率口径）");
            p.putNull("判断前当前盘口已投注金额（含初始，投注额×赔率口径）");
            p.putNull("目标金额");
            p.putNull("最大允许金额");
            p.putNull("当前盘口可投注金额");
        }
        p.put("限额公式", LIMIT_FORMULA);
        p.put("是否触发限额拦截", acceptance.limitRejected);
        // Gate2 仅在未命中限额时执行
        p.put("是否进行风险判断", !acceptance.duplicateIgnored && !acceptance.limitRejected);

        putProductExposure(p, "接收前", beforeExp);
        putProductExposure(p, "假设接收后", trialExp);
        ScenarioExposure trialWorst = MatchExposureAggregator.worstScenario(trialExp);
        if (trialWorst != null) {
            putDecimal(p, "假设接收后中奖返还", yuanFromCents(trialWorst.platformPayableSumCents));
        } else {
            p.putNull("假设接收后中奖返还");
        }
        p.put("风险阈值", maxWorstLossYuan);
        p.put("是否超过风险阈值", acceptance.exposureRejected);

        p.put("订单处理结果", decision);
        p.put(
                "拦截类型",
                acceptance.rejectReason == MatchTriggerAcceptance.RejectReason.NONE
                        ? ""
                        : acceptance.rejectReason.name());
        p.put(
                "拦截原因",
                acceptance.limitRejected
                        ? "等比例限额拒单"
                        : acceptance.exposureRejected
                                ? "比分矩阵最差敞口超阈"
                                : acceptance.duplicateIgnored ? "重复订单" : "");

        if (resolvedOpt.isPresent()) {
            ResolvedOutcome r = resolvedOpt.get();
            String gk = LimitSelectionResolver.groupKey(r.marketType, r.line);
            MarketGroupLimit afterG = findGroup(afterGroups, gk);
            if (afterG != null) {
                putDecimal(
                        p, "处理后组内已投注金额（含初始，投注额×赔率口径）", afterG.groupTotalStake);
                OutcomeLimitRow row = findOutcome(afterG, r.selectionKey);
                if (row != null) {
                    putDecimal(
                            p,
                            "处理后当前盘口已投注金额（含初始，投注额×赔率口径）",
                            row.stake);
                } else {
                    p.putNull("处理后当前盘口已投注金额（含初始，投注额×赔率口径）");
                }
            } else {
                p.putNull("处理后组内已投注金额（含初始，投注额×赔率口径）");
                p.putNull("处理后当前盘口已投注金额（含初始，投注额×赔率口径）");
            }
        } else {
            p.putNull("处理后组内已投注金额（含初始，投注额×赔率口径）");
            p.putNull("处理后当前盘口已投注金额（含初始，投注额×赔率口径）");
        }
        putProductExposure(p, "实际接收后", afterExp);

        p.putNull("Genius判断结果");
        p.put("最终判断结果", decision);
        return p;
    }

    private static void putProductExposure(ObjectNode p, String prefix, ExposureSummary exp) {
        if (exp == null) {
            p.putNull(prefix + "累计投注金额");
            p.putNull(prefix + "最差比分");
            p.putNull(prefix + "最差盈亏");
            return;
        }
        ScenarioExposure w = MatchExposureAggregator.worstScenario(exp);
        long stakeCents = w != null ? w.stakeSumCents : 0L;
        putDecimal(p, prefix + "累计投注金额", yuanFromCents(stakeCents));
        p.put(prefix + "最差比分", w == null ? "" : w.scenario.scoreLabel());
        putDecimal(
                p,
                prefix + "最差盈亏",
                w == null
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(w.bookmakerPnlCents)
                                .movePointLeft(2)
                                .setScale(2, RoundingMode.HALF_UP));
    }

    private static void putExposureBlock(ObjectNode parent, String name, ExposureSummary exp) {
        ObjectNode n = parent.putObject(name);
        if (exp == null) {
            n.putNull("stakeSumYuan");
            n.putNull("worstScore");
            n.putNull("worstBookmakerPnlYuan");
            n.putNull("worstWinPayoutYuan");
            return;
        }
        ScenarioExposure w = MatchExposureAggregator.worstScenario(exp);
        n.put("stakeSumYuan", yuanFromCents(w == null ? 0L : w.stakeSumCents).doubleValue());
        n.put("worstScore", w == null ? "" : w.scenario.scoreLabel());
        n.put(
                "worstBookmakerPnlYuan",
                w == null
                        ? 0
                        : BigDecimal.valueOf(w.bookmakerPnlCents)
                                .movePointLeft(2)
                                .setScale(2, RoundingMode.HALF_UP)
                                .doubleValue());
        n.put(
                "worstWinPayoutYuan",
                w == null ? 0 : yuanFromCents(w.platformPayableSumCents).doubleValue());
        n.put("maxBookmakerLossYuan", centsToYuanAbs(exp.maxBookmakerLossCents));
    }

    private static MarketGroupLimit findGroup(List<MarketGroupLimit> groups, String groupKey) {
        for (MarketGroupLimit g : groups) {
            if (groupKey.equals(g.groupKey)) {
                return g;
            }
        }
        return null;
    }

    private static OutcomeLimitRow findOutcome(MarketGroupLimit g, String selection) {
        for (OutcomeLimitRow row : g.rows) {
            if (selection.equals(row.selection)) {
                return row;
            }
        }
        return null;
    }

    private static String matchLabel(FootballSportsOrder order) {
        String home = nullToEmpty(order.homeTeam);
        String away = nullToEmpty(order.awayTeam);
        if (!home.isEmpty() || !away.isEmpty()) {
            return home + " vs " + away;
        }
        return nullToEmpty(order.fixtureId);
    }

    private static BigDecimal yuanFromCents(long cents) {
        return BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    private static double centsToYuanAbs(long cents) {
        return Math.abs(cents) / 100.0;
    }

    private static void putDecimal(ObjectNode n, String field, BigDecimal value) {
        if (value == null) {
            n.putNull(field);
        } else {
            n.put(field, value);
        }
    }

    private static String text(ObjectNode n, String field) {
        return text(n, field, "");
    }

    private static String text(ObjectNode n, String field, String fallback) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return fallback;
        }
        String v = n.get(field).asText("");
        return v.isEmpty() ? fallback : v;
    }

    private static BigDecimal decimalOrNull(ObjectNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return null;
        }
        return n.get(field).decimalValue();
    }

    private static BigDecimal decimalOrZero(ObjectNode n, String field) {
        BigDecimal v = decimalOrNull(n, field);
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
