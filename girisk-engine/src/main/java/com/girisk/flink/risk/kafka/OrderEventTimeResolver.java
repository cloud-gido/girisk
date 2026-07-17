package com.girisk.flink.risk.kafka;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.time.OrderEventTimes;

import java.util.Locale;

/** 解析下单事件时间；JSON 暂未带时间时可用处理时间兜底（并打日志）。 */
public final class OrderEventTimeResolver {

    private OrderEventTimeResolver() {}

    public static long resolveMillis(FootballSportsOrder order, long processingTimeMs) {
        if (order.orderTime != null && !order.orderTime.isBlank()) {
            return OrderEventTimes.parseOrderTimeMillis(order.orderTime);
        }
        System.err.printf(
                Locale.ROOT,
                "[下单时间兜底] orderId=%s fixtureId=%s 使用处理时间=%d（待上游补充 orderTime）%n",
                order.orderId,
                order.fixtureId,
                processingTimeMs);
        return processingTimeMs;
    }
}
