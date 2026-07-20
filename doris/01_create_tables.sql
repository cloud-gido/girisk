-- ============================================================
-- GiRisk 审计平面 Doris DDL
-- 链路：Kafka (decision / config / status) → Routine Load → 三表
-- ============================================================

CREATE DATABASE IF NOT EXISTS girisk;
USE girisk;

-- ── 决策审计（decision topic 原样 + 拉平列）────────────────
CREATE TABLE IF NOT EXISTS risk_decision_log (
    decision_date       DATE         NOT NULL COMMENT '分区日期（decisionTime）',
    operator_id         VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '租户',
    fixture_id          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '场次',
    order_id            VARCHAR(128) NOT NULL COMMENT '订单号',
    decision_time       DATETIME(3)  NOT NULL COMMENT '决策时间',
    trace_id            VARCHAR(128) COMMENT '全链路 trace',
    user_id             VARCHAR(64),
    decision            VARCHAR(16)  COMMENT 'PASS/REJECT/LIMIT/REVIEW',
    engine_build        VARCHAR(64),
    config_epoch        BIGINT       COMMENT 'versions.configEpoch',
    stake_cents         BIGINT,
    odds                VARCHAR(32),
    payout_cents        BIGINT,
    reasons             JSON         COMMENT '命中理由数组',
    versions            JSON         COMMENT '版本三元组',
    evidence            JSON         COMMENT 'Gate 证据',
    feature_snapshot    JSON         COMMENT '现场快照（若有）',
    raw                 STRING       COMMENT '完整 Kafka JSON 原文'
)
DUPLICATE KEY(decision_date, operator_id, fixture_id, order_id)
PARTITION BY RANGE(decision_date) ()
DISTRIBUTED BY HASH(order_id) BUCKETS AUTO
PROPERTIES (
    "dynamic_partition.enable" = "true",
    "dynamic_partition.time_unit" = "DAY",
    "dynamic_partition.start" = "-730",
    "dynamic_partition.end" = "3",
    "dynamic_partition.prefix" = "p",
    "replication_num" = "1"
);

-- ── 配置发布审计（config topic）────────────────────────────
CREATE TABLE IF NOT EXISTS risk_config_log (
    publish_date        DATE         NOT NULL COMMENT '分区日期',
    config_epoch        BIGINT       NOT NULL COMMENT '配置世代',
    published_at        DATETIME(3)  NOT NULL COMMENT '发布时间',
    scope               VARCHAR(64)  DEFAULT 'global',
    approval_ticket     VARCHAR(128),
    published_by        VARCHAR(64),
    param_set           STRING       COMMENT 'paramSetJson 原文（JSON 字符串）',
    rule_set            STRING       COMMENT 'ruleSetJson 原文（JSON 字符串）',
    raw                 STRING       COMMENT '完整 Kafka JSON 原文'
)
DUPLICATE KEY(publish_date, config_epoch)
PARTITION BY RANGE(publish_date) ()
DISTRIBUTED BY HASH(config_epoch) BUCKETS AUTO
PROPERTIES (
    "dynamic_partition.enable" = "true",
    "dynamic_partition.time_unit" = "DAY",
    "dynamic_partition.start" = "-3650",
    "dynamic_partition.end" = "3",
    "dynamic_partition.prefix" = "p",
    "replication_num" = "1"
);

-- ── 订单状态审计（status topic，含 SETTLED 盈亏）────────────
CREATE TABLE IF NOT EXISTS risk_order_status_log (
    status_date         DATE         NOT NULL COMMENT '分区日期',
    order_id            VARCHAR(128) NOT NULL,
    status_time         DATETIME(3)  NOT NULL,
    trace_id            VARCHAR(128),
    status              VARCHAR(32)  COMMENT 'CONFIRMED/REJECTED/CASHED_OUT/SETTLED/...',
    settle_pnl_cents    BIGINT       COMMENT '平台盈亏（分），SETTLED 时有值',
    operator_id         VARCHAR(64),
    fixture_id          VARCHAR(64),
    user_id             VARCHAR(64),
    raw                 STRING       COMMENT '完整 Kafka JSON 原文'
)
DUPLICATE KEY(status_date, order_id, status_time)
PARTITION BY RANGE(status_date) ()
DISTRIBUTED BY HASH(order_id) BUCKETS AUTO
PROPERTIES (
    "dynamic_partition.enable" = "true",
    "dynamic_partition.time_unit" = "DAY",
    "dynamic_partition.start" = "-730",
    "dynamic_partition.end" = "3",
    "dynamic_partition.prefix" = "p",
    "replication_num" = "1"
);
