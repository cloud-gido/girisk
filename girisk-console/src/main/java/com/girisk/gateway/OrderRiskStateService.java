package com.girisk.gateway;

import com.girisk.common.exception.BusinessException;
import com.girisk.sports.store.ExposureStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 订单风控状态机：decide(reserve) → confirm | cancel → settle。
 */
@Service
public class OrderRiskStateService {

    private final ExposureStore exposureStore;

    public OrderRiskStateService(ExposureStore exposureStore) {
        this.exposureStore = exposureStore;
    }

    public Map<String, Object> confirm(String orderId) {
        if (exposureStore.getSettledPnl(orderId).isPresent()) {
            throw new BusinessException("订单已结算，无法确认: " + orderId);
        }
        boolean ok = exposureStore.confirmReserve(orderId);
        if (!ok) {
            Optional<ExposureStore.ReserveRecord> r = exposureStore.getReserve(orderId);
            if (r.isEmpty()) {
                exposureStore.markOrderProcessed(orderId);
            } else {
                throw new BusinessException("预占状态不可确认: " + orderId);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("status", "CONFIRMED");
        result.put("reserve", exposureStore.getReserve(orderId).orElse(null));
        return result;
    }

    public Map<String, Object> cancel(String orderId) {
        boolean ok = exposureStore.cancelReserve(orderId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("status", "CANCELLED");
        result.put("released", ok);
        return result;
    }

    public Map<String, Object> settle(String orderId, Long settlePnlCents) {
        Optional<ExposureStore.ReserveRecord> r = exposureStore.getReserve(orderId);
        if (r.isPresent() && ("CONFIRMED".equals(r.get().status()) || "PENDING".equals(r.get().status()))) {
            exposureStore.cancelReserve(orderId);
        }
        long pnl = settlePnlCents != null ? settlePnlCents : 0L;
        exposureStore.markSettled(orderId, pnl);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("status", "SETTLED");
        result.put("settlePnlCents", pnl);
        return result;
    }

    public Map<String, Object> status(String orderId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        Optional<Long> settled = exposureStore.getSettledPnl(orderId);
        if (settled.isPresent()) {
            result.put("status", "SETTLED");
            result.put("settlePnlCents", settled.get());
            return result;
        }
        Optional<ExposureStore.ReserveRecord> r = exposureStore.getReserve(orderId);
        if (r.isPresent()) {
            result.put("status", r.get().status());
            result.put("reserve", r.get());
            return result;
        }
        result.put("status", exposureStore.isOrderProcessed(orderId) ? "CONFIRMED" : "UNKNOWN");
        return result;
    }
}
