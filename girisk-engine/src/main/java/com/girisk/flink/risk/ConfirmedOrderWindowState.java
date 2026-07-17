package com.girisk.flink.risk;

import com.girisk.flink.risk.excel.FootballSportsOrder;
import com.girisk.flink.risk.model.OrderPostStatus;
import com.girisk.flink.risk.model.OrderPostStatusUpdate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 场次 CONFIRMED 订单窗口（post topic 驱动写入）。 */
final class ConfirmedOrderWindowState {

    private ConfirmedOrderWindowState() {}

    static void applyPostUpdate(
            List<MatchExposureKafkaProcessFunction.StoredOrder> stored,
            OrderPostStatusUpdate update) {
        String orderId = normalizeOrderId(update.orderId);
        if (orderId.isEmpty()) {
            return;
        }
        switch (update.status) {
            case CONFIRMED:
                upsertConfirmed(stored, update, orderId);
                break;
            case REJECTED:
            case CASHED_OUT:
                removeByOrderId(stored, orderId);
                logPostRemoval(update);
                break;
            default:
                break;
        }
    }

    private static void upsertConfirmed(
            List<MatchExposureKafkaProcessFunction.StoredOrder> stored,
            OrderPostStatusUpdate update,
            String orderId) {
        if (update.order == null) {
            System.err.printf(
                    Locale.ROOT,
                    "[post-CONFIRMED] 缺少订单明细 orderId=%s fixtureId=%s，跳过写入%n",
                    orderId,
                    update.fixtureId);
            return;
        }
        removeByOrderId(stored, orderId);
        stored.add(new MatchExposureKafkaProcessFunction.StoredOrder(update.eventTimeMs, update.order));
        stored.sort(Comparator.comparingLong(s -> s.orderTimeMs));
    }

    private static void removeByOrderId(
            List<MatchExposureKafkaProcessFunction.StoredOrder> stored, String orderId) {
        stored.removeIf(s -> orderId.equals(normalizeOrderId(s.order.orderId)));
    }

    private static void logPostRemoval(OrderPostStatusUpdate update) {
        System.err.printf(
                Locale.ROOT,
                "[post-%s] 从敞口窗口移除 orderId=%s fixtureId=%s%n",
                update.status,
                update.orderId,
                update.fixtureId);
    }

    static List<FootballSportsOrder> toOrders(
            List<MatchExposureKafkaProcessFunction.StoredOrder> stored) {
        List<FootballSportsOrder> orders = new ArrayList<>(stored.size());
        for (MatchExposureKafkaProcessFunction.StoredOrder s : stored) {
            orders.add(s.order);
        }
        return orders;
    }

    static String normalizeOrderId(String orderId) {
        return orderId == null ? "" : orderId.trim();
    }

    static boolean containsOrderId(
            List<MatchExposureKafkaProcessFunction.StoredOrder> stored, String dedupeKey) {
        for (MatchExposureKafkaProcessFunction.StoredOrder s : stored) {
            if (dedupeKey.equals(normalizeOrderId(s.order.orderId))) {
                return true;
            }
        }
        return false;
    }
}
