package com.girisk.flink.risk.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 订单解析跳过/告警：写入 TaskManager 日志，Flink Web UI 可见。 */
public final class OrderParseLog {

    private static final Logger LOG = LoggerFactory.getLogger(OrderParseLog.class);

    private OrderParseLog() {}

    public static void warnSkip(String reason, String raw) {
        LOG.warn("[parse-order] 跳过: {} | {}", reason, truncate(raw));
    }

    public static void warnUnexpectedEnvelope(TradingEnvelopePeek peek, String raw) {
        LOG.warn(
                "[parse-order] 跳过: 非风控试探格式（期望 OrderRiskCheckEvent + status=PENDING），{} | {}",
                peek.describeMismatch(),
                truncate(raw));
    }

    private static String truncate(String line) {
        if (line == null) {
            return "";
        }
        return line.length() <= 300 ? line : line.substring(0, 300) + "...";
    }
}
