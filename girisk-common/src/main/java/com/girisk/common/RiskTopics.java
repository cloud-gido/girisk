package com.girisk.common;

/** Shared Kafka topic names (console + Flink job). */
public final class RiskTopics {

    /** Trading → engine: pre-bet risk check. */
    public static final String RISK_CHECK_PRE = "girisk.trading.order.risk-check.v1";

    /** Trading → engine: post status (CONFIRMED / REJECTED / SETTLED…). */
    public static final String RISK_CHECK_POST = "girisk.trading.order.risk-check.post.v1";

    /** Console → engine: versioned config (compacted). */
    public static final String RISK_CONFIG = "girisk.config.v1";

    /** Engine → trading / console / audit: single decision egress. */
    public static final String RISK_DECISION = "girisk.decision.v1";

    /** Live score feed. */
    public static final String LIVE_SCORE = "girisk.sportsdata.fixture.match.summary";

    private RiskTopics() {}
}
