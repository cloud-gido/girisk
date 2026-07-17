package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.excel.FootballSportsOrder;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 解析 Kafka 订单 CSV 一行（英文/中文逗号均可）。
 *
 * <p>字段顺序：赛事ID,订单号,下单时间,用户ID,联赛,主队,客队,开赛时间,玩法,过关方式,盘口,投注项,赔率,投注金额
 */
public final class KafkaFootballOrderCsvParser {

    private static final int FIELD_COUNT = 14;
    private static final Pattern MONEY =
            Pattern.compile("[^0-9.\\-]");

    private KafkaFootballOrderCsvParser() {}

    public static FootballSportsOrder parse(String rawLine) {
        if (rawLine == null) {
            throw new IllegalArgumentException("空行");
        }
        String line = OrderCsvLineNormalizer.normalizeLine(rawLine);
        if (line.isEmpty() || line.startsWith("#")) {
            throw new IllegalArgumentException("空行或注释");
        }
        String[] parts = line.split(",", FIELD_COUNT);
        if (parts.length != FIELD_COUNT) {
            throw new IllegalArgumentException(
                    "需要 " + FIELD_COUNT + " 个逗号分隔字段，实际 " + parts.length + " 段");
        }
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }

        FootballSportsOrder o = new FootballSportsOrder();
        o.fixtureId = require(parts[0], "赛事ID");
        o.orderId = require(parts[1], "订单号");
        o.orderTime = parts[2];
        o.userId = parts[3];
        o.league = parts[4];
        o.homeTeam = parts[5];
        o.awayTeam = parts[6];
        o.kickoffTime = parts[7];
        o.playType = require(parts[8], "玩法");
        o.parlayType = parts[9];
        o.handicapText = normalizeHandicap(parts[10]);
        o.selection = normalizeSelection(require(parts[11], "投注项"));
        o.odds = Double.parseDouble(require(parts[12], "赔率"));
        o.stakeYuan = parseStakeYuan(parts[13]);
        return o;
    }

    private static long parseStakeYuan(String raw) {
        String cleaned = MONEY.matcher(raw.trim()).replaceAll("");
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("投注金额无效: " + raw);
        }
        return Math.round(Double.parseDouble(cleaned));
    }

    private static String normalizeHandicap(String handicap) {
        if (handicap == null || handicap.isEmpty() || "-".equals(handicap) || "无".equals(handicap)) {
            return "无";
        }
        return collapseSpaces(handicap);
    }

    private static String normalizeSelection(String selection) {
        return collapseSpaces(selection);
    }

    /** 「主队 -0.25」「客队 +0.75」→ 去掉空白，便于盘口/选项解析。 */
    static String collapseSpaces(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("\\s+", "");
    }

    private static String require(String v, String name) {
        if (v == null || v.isEmpty()) {
            throw new IllegalArgumentException("缺少" + name);
        }
        return v;
    }

    public static String formatSpec() {
        return String.format(
                Locale.ROOT,
                "赛事ID,订单号,下单时间,用户ID,联赛,主队,客队,开赛时间,玩法,过关方式,盘口,投注项,赔率,投注金额（共%d段）",
                FIELD_COUNT);
    }
}
