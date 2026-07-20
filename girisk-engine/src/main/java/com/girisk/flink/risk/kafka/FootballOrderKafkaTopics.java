package com.girisk.flink.risk.kafka;

import com.girisk.common.RiskTopics;

/**
 * 足球订单作业 Kafka topic。
 *
 * <p>默认只写 {@link #DECISION}；DETAIL/SUMMARY/LIMIT/BUSINESS 为兼容旧链路的常量名，
 * 作业默认不再自动创建/写出（需显式 {@code --sink.topic.*}）。
 */
public final class FootballOrderKafkaTopics {

    /** @deprecated 旧出口，默认不写；仅作 CLI 显式重开时的名字常量。 */
    @Deprecated
    public static final String DETAIL = "girisk.football.detail.result";

    /** @deprecated 旧出口，默认不写。 */
    @Deprecated
    public static final String SUMMARY = "girisk.football.summary.result";

    /** @deprecated 旧出口，默认不写。 */
    @Deprecated
    public static final String LIMIT = "girisk.football.limit.result";

    /** @deprecated 旧出口，默认不写。 */
    @Deprecated
    public static final String BUSINESS = "girisk.football.business.result";

    /** 滚球实时比分（Genius FixtureScoreUpdatedEvent / FootballMatchSummary / 简易 JSON）。 */
    public static final String LIVE_SCORE = RiskTopics.LIVE_SCORE;

    /** 下单前风控试探（PENDING）。 */
    public static final String RISK_CHECK_PRE = RiskTopics.RISK_CHECK_PRE;

    /** 下单后状态回传（CONFIRMED / REJECTED / CASHED_OUT）。 */
    public static final String RISK_CHECK_POST = RiskTopics.RISK_CHECK_POST;

    /** 唯一决策出口（交易 / 运营台 / 审计）。 */
    public static final String DECISION = RiskTopics.RISK_DECISION;

    /** 配置下发（运营台 → 引擎）。 */
    public static final String CONFIG = RiskTopics.RISK_CONFIG;

    private FootballOrderKafkaTopics() {}
}
