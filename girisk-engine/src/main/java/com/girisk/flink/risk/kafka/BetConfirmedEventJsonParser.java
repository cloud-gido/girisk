package com.girisk.flink.risk.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.flink.risk.excel.FootballSportsOrder;

import java.util.Locale;

/**
 * 交易侧 envelope JSON（envelopeVersion=1）→ {@link FootballSportsOrder}。
 *
 * <p>支持 {@code eventType}：
 *
 * <ul>
 *   <li>{@code BetConfirmedEvent}：仅 {@code status=CONFIRMED}
 *   <li>{@code OrderRiskCheckEvent}：下单前风控试探，接受 {@code status=PENDING}（及空 status）
 * </ul>
 *
 * <p>联赛 / 主客 / 开赛可由 {@code fixtureId} 维表可选补全；未配置或缺失时保持空，不阻塞计算。
 * 当前仅支持 {@code betType=SINGLE}，取首条 leg。支持两种 leg 结构：
 *
 * <ul>
 *   <li>{@code payload.legs[0].legPick}（旧）
 *   <li>{@code payload.combinations[0].legs[0].market + selection}（新）
 * </ul>
 */
public final class BetConfirmedEventJsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BetConfirmedEventJsonParser() {}

    public static boolean looksLikeJson(String raw) {
        if (raw == null) {
            return false;
        }
        String t = raw.trim();
        return t.startsWith("{") && t.endsWith("}");
    }

    public static FootballSportsOrder parse(String json) {
        return parseWithStatusPolicy(json, StatusPolicy.RISK_PIPELINE);
    }

    /** post topic CONFIRMED（phase=POST_CONFIRM）。 */
    public static FootballSportsOrder parsePostConfirmed(String json) {
        return parseWithStatusPolicy(json, StatusPolicy.POST_CONFIRMED);
    }

    private enum StatusPolicy {
        RISK_PIPELINE,
        POST_CONFIRMED
    }

    private static FootballSportsOrder parseWithStatusPolicy(String json, StatusPolicy policy) {
        try {
            JsonNode root = MAPPER.readTree(json);
            String eventType = text(root, "eventType");
            if (!isSupportedEventType(eventType)) {
                throw new IllegalArgumentException("暂不支持的 eventType: " + eventType);
            }
            JsonNode payload = require(root, "payload");
            requireAcceptedStatus(eventType, payload, policy);
            String orderId = resolveOrderId(root, payload);
            JsonNode leg = resolveFirstLeg(payload);
            ResolvedLegPick pick = resolveLegPick(leg);

            FootballSportsOrder o = new FootballSportsOrder();
            o.operatorId = parseOperatorId(root);
            o.eventId = text(root, "eventId");
            o.fixtureId = fixtureIdText(leg);
            o.orderId = orderId;
            o.orderTime = text(payload, "betTime");
            o.userId = firstNonEmpty(text(payload, "playerId"), text(payload, "userId"));
            o.league = "";
            o.homeTeam = "";
            o.awayTeam = "";
            o.kickoffTime = "";
            o.parlayType = mapBetType(text(payload, "betType"));
            o.playType = pick.marketType;
            o.handicapText = formatHandicap(pick.marketType, pick.side, pick.line);
            o.selection = mapLegPickSelection(pick.marketType, pick.side);
            o.odds = pick.price;
            o.stakeYuan = parseStakeYuan(payload);
            return o;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("交易订单 JSON 解析失败: " + e.getMessage(), e);
        }
    }

    private static boolean isSupportedEventType(String eventType) {
        if (eventType.isEmpty()) {
            return true;
        }
        return "BetConfirmedEvent".equalsIgnoreCase(eventType)
                || "OrderRiskCheckEvent".equalsIgnoreCase(eventType);
    }

    /**
     * {@code BetConfirmedEvent} 仅已确认；{@code OrderRiskCheckEvent} 为 PRE_CONFIRM 试探单（通常
     * {@code status=PENDING}）。
     */
    private static void requireAcceptedStatus(String eventType, JsonNode payload, StatusPolicy policy) {
        String status = text(payload, "status");
        if ("OrderRiskCheckEvent".equalsIgnoreCase(eventType)) {
            if (policy == StatusPolicy.POST_CONFIRMED) {
                if (!"CONFIRMED".equalsIgnoreCase(status)) {
                    throw new IllegalArgumentException(
                            "post CONFIRMED 解析期望 status=CONFIRMED，当前 status=" + status);
                }
                return;
            }
            if (status.isEmpty()
                    || "PENDING".equalsIgnoreCase(status)
                    || "PRE_CONFIRM".equalsIgnoreCase(status)
                    || "CONFIRMED".equalsIgnoreCase(status)) {
                return;
            }
            throw new IllegalArgumentException(
                    "OrderRiskCheckEvent 期望 status=PENDING，当前 status="
                            + status
                            + " phase="
                            + text(payload, "phase"));
        }
        if (!"CONFIRMED".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException(
                    "BetConfirmedEvent 仅处理 status=CONFIRMED，当前 status="
                            + (status.isEmpty() ? "(空)" : status));
        }
    }

    private static final class ResolvedLegPick {
        final String marketType;
        final String side;
        final double line;
        final double price;

        ResolvedLegPick(String marketType, String side, double line, double price) {
            this.marketType = marketType;
            this.side = side;
            this.line = line;
            this.price = price;
        }
    }

    private static JsonNode resolveFirstLeg(JsonNode payload) {
        JsonNode legs = payload.get("legs");
        if (legs != null && legs.isArray() && !legs.isEmpty()) {
            return legs.get(0);
        }
        JsonNode combinations = payload.get("combinations");
        if (combinations != null && combinations.isArray() && !combinations.isEmpty()) {
            JsonNode comboLegs = combinations.get(0).get("legs");
            if (comboLegs != null && comboLegs.isArray() && !comboLegs.isEmpty()) {
                return comboLegs.get(0);
            }
        }
        throw new IllegalArgumentException("payload.legs 或 payload.combinations[0].legs 为空");
    }

    private static ResolvedLegPick resolveLegPick(JsonNode leg) {
        JsonNode legPick = leg.get("legPick");
        if (legPick != null && !legPick.isNull()) {
            double line = legPick.has("line") ? legPick.get("line").asDouble() : Double.NaN;
            return new ResolvedLegPick(
                    text(legPick, "type"),
                    text(legPick, "side"),
                    line,
                    requirePrice(leg));
        }
        JsonNode market = leg.get("market");
        JsonNode selection = leg.get("selection");
        if (market != null && !market.isNull() && selection != null && !selection.isNull()) {
            double line = market.has("line") ? market.get("line").asDouble() : Double.NaN;
            return new ResolvedLegPick(
                    text(market, "type"),
                    text(selection, "side"),
                    line,
                    requirePrice(leg));
        }
        throw new IllegalArgumentException("leg 缺少 legPick 或 market+selection");
    }

    private static double requirePrice(JsonNode leg) {
        JsonNode price = leg.get("price");
        if (price == null || price.isNull()) {
            throw new IllegalArgumentException("leg 缺少 price");
        }
        return price.asDouble();
    }

    private static long parseOperatorId(JsonNode root) {
        JsonNode v = root.get("operatorId");
        if (v == null || v.isNull()) {
            return 0L;
        }
        return v.asLong();
    }

    private static String resolveOrderId(JsonNode root, JsonNode payload) {
        String orderId = text(root, "orderId");
        if (!orderId.isEmpty()) {
            return orderId;
        }
        orderId = text(payload, "orderId");
        if (!orderId.isEmpty()) {
            return orderId;
        }
        orderId = text(root, "aggregateId");
        if (!orderId.isEmpty()) {
            return orderId;
        }
        throw new IllegalArgumentException("缺少 orderId（根 orderId / payload.orderId / aggregateId）");
    }

    /** stake 为元（主币种单位，可带小数），如 {@code 1.0} BRL → 1 元。 */
    static long parseStakeYuan(JsonNode payload) {
        JsonNode stake = require(payload, "stake");
        if (stake.isIntegralNumber()) {
            return stake.asLong();
        }
        return Math.round(stake.asDouble());
    }

    private static String fixtureIdText(JsonNode leg) {
        JsonNode v = leg.get("fixtureId");
        if (v == null || v.isNull()) {
            throw new IllegalArgumentException("缺少字段: fixtureId");
        }
        if (v.isNumber()) {
            return v.asText();
        }
        return v.asText("").trim();
    }

    private static String mapBetType(String betType) {
        if (betType == null || betType.isEmpty()) {
            return "单关";
        }
        String t = betType.toUpperCase(Locale.ROOT);
        if ("SINGLE".equals(t)) {
            return "单关";
        }
        if ("PARLAY".equals(t) || "MULTIPLE".equals(t)) {
            return "串关";
        }
        return betType;
    }

    private static String formatHandicap(String marketType, String side, double line) {
        if (isMatchResultMarket(marketType)) {
            return "无";
        }
        if (Double.isNaN(line)) {
            return "无";
        }
        if (line == 0.0 && isOverUnderMarket(marketType)) {
            return "无";
        }
        String team = "HOME".equalsIgnoreCase(side) ? "主队" : "AWAY".equalsIgnoreCase(side) ? "客队" : side;
        if (line > 0) {
            return team + " +" + trimDouble(line);
        }
        if (line < 0) {
            return team + " " + trimDouble(line);
        }
        return team + " 0";
    }

    static String mapLegPickSelection(String marketType, String side) {
        String s = side.toUpperCase(Locale.ROOT);
        if (isMatchResultMarket(marketType)) {
            if ("HOME".equals(s)) {
                return "主胜";
            }
            if ("AWAY".equals(s)) {
                return "客胜";
            }
            if ("DRAW".equals(s)) {
                return "平局";
            }
        }
        if ("HOME".equals(s)) {
            return "主队";
        }
        if ("AWAY".equals(s)) {
            return "客队";
        }
        if ("DRAW".equals(s)) {
            return "平局";
        }
        if ("OVER".equals(s)) {
            return "大球";
        }
        if ("UNDER".equals(s)) {
            return "小球";
        }
        return side;
    }

    private static boolean isMatchResultMarket(String marketType) {
        String t = marketType.toUpperCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        return t.contains("1X2")
                || t.contains("MATCH RESULT")
                || t.contains("MONEYLINE")
                || t.contains("胜平负");
    }

    private static boolean isOverUnderMarket(String marketType) {
        String t = marketType.toUpperCase(Locale.ROOT);
        return t.contains("OU")
                || t.contains("OVER")
                || t.contains("TOTAL")
                || t.contains("大小");
    }

    private static String trimDouble(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    private static String firstNonEmpty(String a, String b) {
        return !a.isEmpty() ? a : b;
    }

    private static JsonNode require(JsonNode parent, String field) {
        JsonNode n = parent.get(field);
        if (n == null || n.isNull()) {
            throw new IllegalArgumentException("缺少字段: " + field);
        }
        return n;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return "";
        }
        if (v.isNumber()) {
            return v.asText();
        }
        return v.asText("").trim();
    }
}
