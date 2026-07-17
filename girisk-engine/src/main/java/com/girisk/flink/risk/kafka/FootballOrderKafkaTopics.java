package com.girisk.flink.risk.kafka;

import com.girisk.common.RiskTopics;

/** 足球订单作业默认 Kafka topic。 */
public final class FootballOrderKafkaTopics {

    /** 明细：订单 × 假设比分（每格一行）。 */
    public static final String DETAIL = "girisk.football.detail.result";

    /** 汇总：场次嵌套快照 schemaVersion=7（每场每次触发一条）。 */
    public static final String SUMMARY = "girisk.football.summary.result";

    /** 等比例限额：场次各盘口 b_max 快照 schemaVersion=3（含 trigger 前限额）。 */
    public static final String LIMIT = "girisk.football.limit.result";

    /** 业务方汇总：summaryData（无 assumedScores）+ limitData（与 Limit v3 相同）。 */
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
