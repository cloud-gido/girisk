package com.girisk.flink.risk;

import com.girisk.flink.risk.excel.FootballSportsOrder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * PENDING 预留账本：PASS 后、CONFIRMED 前占用限额/敞口基数，防并发击穿。
 */
public final class PendingReserveBook implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final class Entry implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String orderId;
        public final long expireAtMs;
        public final FootballSportsOrder order;

        public Entry(String orderId, long expireAtMs, FootballSportsOrder order) {
            this.orderId = orderId;
            this.expireAtMs = expireAtMs;
            this.order = order;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public List<FootballSportsOrder> toOrders(long nowMs) {
        purgeExpired(nowMs);
        List<FootballSportsOrder> out = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            out.add(e.order);
        }
        return out;
    }

    public void reserve(FootballSportsOrder order, long expireAtMs) {
        String id = ConfirmedOrderWindowState.normalizeOrderId(order.orderId);
        if (id.isEmpty()) {
            return;
        }
        remove(id);
        entries.add(new Entry(id, expireAtMs, order));
    }

    public void remove(String orderId) {
        String id = ConfirmedOrderWindowState.normalizeOrderId(orderId);
        entries.removeIf(e -> id.equals(e.orderId));
    }

    public int purgeExpired(long nowMs) {
        int before = entries.size();
        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.expireAtMs <= nowMs) {
                System.err.printf(
                        Locale.ROOT,
                        "[PENDING-TTL] 释放预留 orderId=%s expireAt=%d%n",
                        e.orderId,
                        e.expireAtMs);
                it.remove();
            }
        }
        return before - entries.size();
    }

    public List<Entry> snapshot() {
        return List.copyOf(entries);
    }

    public void replaceAll(List<Entry> next) {
        entries.clear();
        entries.addAll(next);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
