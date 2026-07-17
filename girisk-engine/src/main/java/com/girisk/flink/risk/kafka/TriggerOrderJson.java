package com.girisk.flink.risk.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.model.EnrichedFootballOrder;
import com.girisk.flink.risk.settlement.PlayTypeRegistry;

/** 触发本次汇总/限额的订单关键信息（嵌套 JSON）。 */
public final class TriggerOrderJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TriggerOrderJson() {}

    /** Summary / Limit 快照中的 {@code triggerOrder} 对象。 */
    public static ObjectNode nested(EnrichedFootballOrder trigger) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("operatorId", trigger.order.operatorId);
        n.put("eventId", nz(trigger.order.eventId));
        putBetFields(n, trigger.order);
        n.put("orderTimeMs", trigger.orderTimeMs);
        return n;
    }

    /** Detail topic 等顶层订单字段（不含 envelope 级 eventId）。 */
    public static void putFlatOrderFields(ObjectNode n, FootballSportsOrder o) {
        n.put("operatorId", o.operatorId);
        putBetFields(n, o);
    }

    private static void putBetFields(ObjectNode n, FootballSportsOrder o) {
        n.put("fixtureId", nz(o.fixtureId));
        n.put("orderId", nz(o.orderId));
        n.put("orderTime", nz(o.orderTime));
        n.put("userId", nz(o.userId));
        n.put("playType", nz(o.playType));
        n.put("playMarketFamily", PlayTypeRegistry.resolve(o).name());
        n.put("parlayType", nz(o.parlayType));
        n.put("handicap", nz(o.handicapText));
        n.put("selection", nz(o.selection));
        n.put("odds", o.odds);
        n.put("stakeYuan", o.stakeYuan);
        n.put("stakeCents", o.stakeCents());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
