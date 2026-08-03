package com.girisk.stream;

import com.girisk.common.dto.RiskEvaluateRequest;
import com.girisk.common.dto.RiskEvaluateResponse;
import com.girisk.common.enums.RiskDecision;
import com.girisk.gateway.RiskDecisionGateway;
import com.girisk.gateway.RiskDecisionRequest;
import com.girisk.gateway.RiskDecisionResponse;
import com.girisk.stream.model.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderStreamProcessor {

    private static final Logger log = LoggerFactory.getLogger(OrderStreamProcessor.class);

    private final OrderEventMapper mapper;
    private final VelocityCounter velocityCounter;
    private final RiskDecisionGateway gateway;
    private final RealtimeEventHub eventHub;
    private final OrderKafkaPublisher kafkaPublisher;
    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();

    public OrderStreamProcessor(
            OrderEventMapper mapper,
            VelocityCounter velocityCounter,
            RiskDecisionGateway gateway,
            RealtimeEventHub eventHub,
            ObjectProvider<OrderKafkaPublisher> kafkaPublisher) {
        this.mapper = mapper;
        this.velocityCounter = velocityCounter;
        this.gateway = gateway;
        this.eventHub = eventHub;
        this.kafkaPublisher = kafkaPublisher.getIfAvailable();
    }

    public RiskEvaluateResponse process(OrderEvent raw, String source) {
        try {
            int count = velocityCounter.recordAndGetCount(raw.userId());
            BigDecimal amountSum = velocityCounter.recordAndGetAmountSum(raw.userId(), raw.amount());
            OrderEvent enriched = mapper.enrich(
                    raw,
                    raw.orderCount24h() != null ? raw.orderCount24h() : count,
                    raw.amountSum24h() != null ? raw.amountSum24h() : amountSum);

            RiskEvaluateRequest request = mapper.toEvaluateRequest(enriched);
            RiskDecisionResponse decided = gateway.decide(RiskDecisionRequest.fromLegacy(request), source);
            RiskEvaluateResponse response = toLegacy(decided);
            eventHub.publishDecision(response);
            if (kafkaPublisher != null) {
                kafkaPublisher.publishDecision(response);
            }
            processed.incrementAndGet();
            return response;
        } catch (Exception e) {
            errors.incrementAndGet();
            log.error("Process order event failed orderId={}", raw.orderId(), e);
            throw e;
        }
    }

    public RiskEvaluateResponse processJson(String json, String source) throws Exception {
        return process(mapper.parse(json), source);
    }

    private static RiskEvaluateResponse toLegacy(RiskDecisionResponse resp) {
        var hits = resp.reasons() == null ? java.util.List.<String>of()
                : resp.reasons().stream().map(r -> r.ruleId()).toList();
        RiskDecision decision = resp.decision() == RiskDecision.LIMIT ? RiskDecision.REJECT : resp.decision();
        return new RiskEvaluateResponse(
                resp.requestId(), resp.orderId(), decision, resp.riskScore(), resp.riskLevel(),
                hits, resp.reason(), resp.strategyCode(), resp.latencyMs(), resp.caseNo());
    }

    public long processedCount() { return processed.get(); }
    public long errorCount() { return errors.get(); }
}
