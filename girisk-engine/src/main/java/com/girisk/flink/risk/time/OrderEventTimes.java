package com.girisk.flink.risk.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** 解析订单 CSV 中的下单时间 / 开赛时间为事件时间（毫秒）。 */
public final class OrderEventTimes {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter[] FORMATS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.ROOT),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.ROOT)
    };

    private OrderEventTimes() {}

    public static long parseOrderTimeMillis(String orderTime) {
        return parseMillis(orderTime, "下单时间");
    }

    public static long parseKickoffTimeMillis(String kickoffTime) {
        return parseMillis(kickoffTime, "开赛时间");
    }

    private static long parseMillis(String text, String fieldName) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        String s = text.trim();
        try {
            return Instant.parse(s).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // ISO-8601 带时区（如 2026-05-14T08:11:08Z）或继续尝试本地格式
        }
        for (DateTimeFormatter fmt : FORMATS) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(s, fmt);
                return ldt.atZone(ZONE).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        throw new IllegalArgumentException(fieldName + " 格式无法解析: " + text);
    }
}
