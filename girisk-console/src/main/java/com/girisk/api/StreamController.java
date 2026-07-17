package com.girisk.api;

import com.girisk.common.dto.ApiResponse;
import com.girisk.common.dto.RiskEvaluateRequest;
import com.girisk.common.dto.RiskEvaluateResponse;
import com.girisk.config.RiskKafkaProperties;
import com.girisk.gateway.RiskDecisionGateway;
import com.girisk.gateway.RiskDecisionRequest;
import com.girisk.gateway.RiskDecisionResponse;
import com.girisk.common.enums.RiskDecision;
import com.girisk.stream.OrderEventMapper;
import com.girisk.stream.OrderKafkaPublisher;
import com.girisk.stream.OrderStreamProcessor;
import com.girisk.stream.RealtimeEventHub;
import com.girisk.stream.model.OrderEvent;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class StreamController {

    private final OrderStreamProcessor processor;
    private final RealtimeEventHub eventHub;
    private final RiskDecisionGateway gateway;
    private final OrderEventMapper orderEventMapper;
    private final OrderKafkaPublisher kafkaPublisher;
    private final RiskKafkaProperties kafkaProperties;

    public StreamController(
            OrderStreamProcessor processor,
            RealtimeEventHub eventHub,
            RiskDecisionGateway gateway,
            OrderEventMapper orderEventMapper,
            ObjectProvider<OrderKafkaPublisher> kafkaPublisher,
            RiskKafkaProperties kafkaProperties) {
        this.processor = processor;
        this.eventHub = eventHub;
        this.gateway = gateway;
        this.orderEventMapper = orderEventMapper;
        this.kafkaPublisher = kafkaPublisher.getIfAvailable();
        this.kafkaProperties = kafkaProperties;
    }

    @GetMapping("/stream/events")
    public SseEmitter streamEvents() {
        return eventHub.subscribe();
    }

    @GetMapping("/stream/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        boolean kafkaOn = kafkaProperties.isEnabled() && kafkaPublisher != null;
        status.put("kafkaEnabled", kafkaOn);
        status.put("mode", kafkaOn ? "REALTIME_KAFKA" : "REALTIME_LOCAL");
        if (kafkaOn) {
            status.put("bootstrapServers", kafkaPublisher.bootstrapServers());
            status.put("orderTopic", kafkaPublisher.orderTopic());
            status.put("decisionTopic", kafkaPublisher.decisionTopic());
        }
        status.put("processedCount", processor.processedCount());
        status.put("errorCount", processor.errorCount());
        status.put("sseSubscribers", eventHub.subscriberCount());
        return ApiResponse.ok(status);
    }

    @PostMapping("/stream/mock-order")
    public ApiResponse<?> mockOrder(@RequestBody(required = false) Map<String, Object> body) throws Exception {
        String json = buildMockOrderJson(body);
        if (kafkaPublisher != null) {
            kafkaPublisher.publishOrder(json);
            OrderEvent event = orderEventMapper.parse(json);
            return ApiResponse.ok(Map.of(
                    "via", "KAFKA",
                    "orderId", event.orderId(),
                    "topic", kafkaPublisher.orderTopic(),
                    "message", "已发送到 Kafka，Consumer 处理后 SSE 推送决策"));
        }
        return ApiResponse.ok(processor.processJson(json, "MOCK"));
    }

    @PostMapping("/stream/mock-burst")
    public ApiResponse<Map<String, Object>> mockBurst(@RequestBody(required = false) Map<String, Object> body) throws Exception {
        int count = 5;
        if (body != null && body.get("count") != null) {
            count = Math.max(1, ((Number) body.get("count")).intValue());
        }
        if (kafkaPublisher != null) {
            for (int i = 0; i < count; i++) {
                kafkaPublisher.publishOrder(buildMockOrderJson(Map.of("index", i)));
            }
            return ApiResponse.ok(Map.of("sent", count, "via", "KAFKA", "topic", kafkaPublisher.orderTopic()));
        }
        for (int i = 0; i < count; i++) {
            processor.processJson(buildMockOrderJson(Map.of("index", i)), "MOCK");
        }
        return ApiResponse.ok(Map.of("sent", count, "via", "LOCAL"));
    }

    @PostMapping("/girisk/evaluate/internal")
    public ApiResponse<RiskEvaluateResponse> evaluateInternal(@Valid @RequestBody RiskEvaluateRequest request) {
        RiskDecisionResponse resp = gateway.decide(RiskDecisionRequest.fromLegacy(request), "API");
        var hits = resp.reasons() == null ? java.util.List.<String>of()
                : resp.reasons().stream().map(r -> r.ruleId()).toList();
        RiskDecision decision = resp.decision() == RiskDecision.LIMIT ? RiskDecision.REJECT : resp.decision();
        return ApiResponse.ok(new RiskEvaluateResponse(
                resp.requestId(), resp.orderId(), decision, resp.riskScore(), resp.riskLevel(),
                hits, resp.reason(), resp.strategyCode(), resp.latencyMs(), resp.caseNo()));
    }

    private String buildMockOrderJson(Map<String, Object> body) throws Exception {
        String[] users = {"U100001", "U200001", "U300001", "U999999", "U500001"};
        int idx = body != null && body.get("index") != null ? ((Number) body.get("index")).intValue() : (int) (Math.random() * users.length);
        double[] amounts = {299, 1580, 8800, 1200, 6800};
        OrderEvent event = new OrderEvent(
                UUID.randomUUID().toString(),
                "ORD-" + System.currentTimeMillis(),
                body != null && body.get("userId") != null ? String.valueOf(body.get("userId")) : users[idx % users.length],
                body != null && body.get("amount") != null ? new BigDecimal(String.valueOf(body.get("amount"))) : BigDecimal.valueOf(amounts[idx % amounts.length]),
                "CNY", "ALIPAY", "113.88." + (idx % 255) + ".1", "DEV-MOCK-" + idx,
                "M001", "GENERAL", "CN", null, null,
                idx == 4, idx * 10, "POST_ORDER", Instant.now());
        return orderEventMapper.toJson(event);
    }
}
