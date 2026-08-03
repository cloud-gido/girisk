package com.girisk.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.common.dto.RiskEvaluateRequest;
import com.girisk.common.dto.RiskEvaluateResponse;
import com.girisk.stream.model.OrderEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderEventMapper {

    private final ObjectMapper objectMapper;

    public OrderEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OrderEvent parse(String json) throws Exception {
        return objectMapper.readValue(json, OrderEvent.class);
    }

    public String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    public RiskEvaluateRequest toEvaluateRequest(OrderEvent event) {
        return new RiskEvaluateRequest(
                event.orderId(), event.userId(), event.amount(),
                event.currency(), event.paymentMethod(), event.ip(), event.deviceId(),
                event.merchantId(), event.productCategory(), event.country(),
                event.orderCount24h(), event.amountSum24h(), event.isNewUser(),
                event.deviceRiskScore(), event.scenario());
    }

    public OrderEvent enrich(OrderEvent event, int orderCount24h, BigDecimal amountSum24h) {
        return new OrderEvent(
                event.eventId(), event.orderId(), event.userId(), event.amount(),
                event.currency(), event.paymentMethod(), event.ip(), event.deviceId(),
                event.merchantId(), event.productCategory(), event.country(),
                orderCount24h, amountSum24h,
                event.isNewUser(), event.deviceRiskScore(), event.scenario(), event.eventTime());
    }
}
