package com.girisk.decision.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.decision.model.DecisionGateSummary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionGateSummarizerTest {

    private final DecisionGateSummarizer summarizer = new DecisionGateSummarizer(new ObjectMapper());

    @Test
    void extractsGate1BMaxAndGate2WorstLoss() {
        String evidence =
                """
                {
                  "rejectReason":"LIMIT",
                  "limitRejected":true,
                  "exposureRejected":false,
                  "limitDelta":0.2,
                  "seedPayoutYuan":5000,
                  "trialWorstLossYuan":510,
                  "maxWorstLossYuan":1000,
                  "gate1TriggerSelection":{
                    "selectionLabel":"客胜",
                    "groupKey":"ONE_X_TWO|",
                    "proposedPayout":1880,
                    "stakeBefore":5094,
                    "targetAmountBefore":5053.67,
                    "maxAllowedAmountBefore":6064.4,
                    "acceptMaxBefore":1617.33,
                    "overLimitBefore":true
                  }
                }
                """;
        String feature =
                """
                {
                  "worstScore":"0:1",
                  "beforeAccept":{"worstBookmakerPnlYuan":-34},
                  "trialAfterAccept":{"worstBookmakerPnlYuan":-510,"worstScore":"0:1","maxBookmakerLossYuan":510},
                  "afterActual":{"worstBookmakerPnlYuan":-34}
                }
                """;

        DecisionGateSummary s = summarizer.summarize(evidence, feature);
        assertTrue(s.limitRejected());
        assertEquals(1617.33, s.gate1().acceptMaxYuan(), 0.001);
        assertEquals(1880.0, s.gate1().proposedPayoutYuan(), 0.001);
        assertEquals(510.0, s.gate2().trialWorstLossYuan(), 0.001);
        assertEquals("0:1", s.gate2().worstScore());
    }
}
