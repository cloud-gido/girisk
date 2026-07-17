package com.girisk.case_.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.case_.model.RiskCase;
import com.girisk.case_.repository.RiskCaseRepository;
import com.girisk.common.exception.BusinessException;
import com.girisk.event.repository.RiskEventRepository;
import com.girisk.gateway.OrderRiskStateService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审核闭环：审核结论回传交易（Kafka/HTTP），并联动订单风控状态机 confirm/cancel。
 */
@Service
public class CaseReviewService {

    private final RiskCaseRepository repository;
    private final RiskEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final OrderRiskStateService orderRiskStateService;
    private final TradingStatusCallbackPublisher callbackPublisher;

    public CaseReviewService(
            RiskCaseRepository repository,
            RiskEventRepository eventRepository,
            ObjectMapper objectMapper,
            ObjectProvider<OrderRiskStateService> orderRiskStateService,
            TradingStatusCallbackPublisher callbackPublisher) {
        this.repository = repository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
        this.orderRiskStateService = orderRiskStateService.getIfAvailable();
        this.callbackPublisher = callbackPublisher;
    }

    public RiskCase review(long id, String decision, String comment, String assignee) {
        RiskCase c = repository.findById(id).orElseThrow(() -> new BusinessException("工单不存在"));
        if (!"PENDING".equals(c.status())) {
            throw new BusinessException("工单已审核，不可重复操作");
        }
        String status = "APPROVED".equals(decision) ? "APPROVED" : "REJECTED";
        repository.review(id, status, decision, comment != null ? comment : "", assignee);

        String suggested = "APPROVED".equals(decision) ? "CONFIRMED" : "REJECTED";
        Map<String, Object> stateResult = Map.of();
        if (orderRiskStateService != null) {
            try {
                if ("APPROVED".equals(decision)) {
                    stateResult = orderRiskStateService.confirm(c.orderId());
                } else {
                    stateResult = orderRiskStateService.cancel(c.orderId());
                }
            } catch (BusinessException ex) {
                stateResult = Map.of("warning", ex.getMessage());
            }
        }

        Map<String, Object> callback = new LinkedHashMap<>();
        callback.put("eventType", "RiskCaseReviewCallback");
        callback.put("caseNo", c.caseNo());
        callback.put("orderId", c.orderId());
        callback.put("userId", c.userId());
        callback.put("operatorId", c.operatorId());
        callback.put("reviewDecision", decision);
        callback.put("status", status);
        callback.put("suggestedOrderStatus", suggested);
        callback.put("assignee", assignee);
        callback.put("comment", comment);
        callback.put("stateMachine", stateResult);

        TradingStatusCallbackPublisher.CallbackResult sent = callbackPublisher.publish(callback);
        callback.put("channel", sent.channel());
        callback.put("delivery", sent.detail());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(callback);
        } catch (Exception e) {
            payload = sent.payloadJson();
        }
        repository.markCallback(id, sent.sent() ? "SENT" : "STORED", payload);
        eventRepository.insert("CASE_CALLBACK", "INFO", c.orderId(), c.userId(),
                "审核结论已回传交易并驱动状态机",
                "decision=" + decision + " → " + suggested + " via " + sent.channel());
        return repository.findById(id).orElseThrow();
    }
}
