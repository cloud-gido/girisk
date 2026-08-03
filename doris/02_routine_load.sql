-- ============================================================
-- Kafka → Doris Routine Load（生产模板）
-- 使用前替换 kafka_broker_list；本地 Docker 见 deploy/doris/02_routine_load.docker.sql
-- 默认 OFFSET_END，避免历史消息落到无分区日导致 Load PAUSED
-- ============================================================

USE girisk;

-- decision.v1
CREATE ROUTINE LOAD girisk.load_risk_decision ON risk_decision_log
COLUMNS(
    decision_time_ms, order_id, fixture_id, operator_id, user_id, trace_id,
    decision, engine_build, config_epoch, stake_cents, odds, payout_cents,
    reasons, versions, evidence, feature_snapshot, raw,
    decision_time = from_unixtime(decision_time_ms / 1000),
    decision_date = to_date(from_unixtime(decision_time_ms / 1000)),
    operator_id = IFNULL(operator_id, ''),
    fixture_id = IFNULL(fixture_id, '')
)
PROPERTIES (
    "format" = "json",
    "jsonpaths" = "[\"$.decisionTimeMs\",\"$.orderId\",\"$.fixtureId\",\"$.operatorId\",\"$.userId\",\"$.traceId\",
        \"$.decision\",\"$.engineBuild\",\"$.versions.configEpoch\",\"$.stakeCents\",\"$.odds\",\"$.payoutCents\",
        \"$.reasons\",\"$.versions\",\"$.evidence\",\"$.featureSnapshot\",\"$.\"]",
    "max_batch_interval" = "10",
    "max_error_number" = "1000",
    "strict_mode" = "false"
)
FROM KAFKA (
    "kafka_broker_list" = "REPLACE_WITH_KAFKA_BROKERS",
    "kafka_topic" = "girisk.decision.v1",
    "property.kafka_default_offsets" = "OFFSET_END",
    "property.group.id" = "doris_girisk_decision_v1"
);

-- config.v1
CREATE ROUTINE LOAD girisk.load_risk_config ON risk_config_log
COLUMNS(
    config_epoch, scope, approval_ticket, published_by, published_at_str,
    param_set_json, rule_set_json, raw,
    published_at = IFNULL(str_to_date(published_at_str, '%Y-%m-%dT%H:%i:%s'), now()),
    publish_date = to_date(IFNULL(str_to_date(published_at_str, '%Y-%m-%dT%H:%i:%s'), now())),
    param_set = param_set_json,
    rule_set = rule_set_json,
    scope = IFNULL(scope, 'global')
)
PROPERTIES (
    "format" = "json",
    "jsonpaths" = "[\"$.configEpoch\",\"$.scope\",\"$.approvalTicket\",\"$.publishedBy\",\"$.publishedAt\",
        \"$.paramSetJson\",\"$.ruleSetJson\",\"$.\"]",
    "max_batch_interval" = "10",
    "max_error_number" = "1000",
    "strict_mode" = "false"
)
FROM KAFKA (
    "kafka_broker_list" = "REPLACE_WITH_KAFKA_BROKERS",
    "kafka_topic" = "girisk.config.v1",
    "property.kafka_default_offsets" = "OFFSET_END",
    "property.group.id" = "doris_girisk_config_v1"
);

-- status post.v1
CREATE ROUTINE LOAD girisk.load_risk_order_status ON risk_order_status_log
COLUMNS(
    order_id, trace_id, status, settle_pnl_cents, operator_id, fixture_id, user_id,
    status_time_ms, raw,
    status_time = IFNULL(from_unixtime(status_time_ms / 1000), now()),
    status_date = to_date(IFNULL(from_unixtime(status_time_ms / 1000), now()))
)
PROPERTIES (
    "format" = "json",
    "jsonpaths" = "[\"$.orderId\",\"$.traceId\",\"$.status\",\"$.settlePnlCents\",\"$.operatorId\",\"$.fixtureId\",\"$.userId\",
        \"$.eventTimeMs\",\"$.\"]",
    "max_batch_interval" = "10",
    "max_error_number" = "1000",
    "strict_mode" = "false"
)
FROM KAFKA (
    "kafka_broker_list" = "REPLACE_WITH_KAFKA_BROKERS",
    "kafka_topic" = "girisk.trading.order.risk-check.post.v1",
    "property.kafka_default_offsets" = "OFFSET_END",
    "property.group.id" = "doris_girisk_order_status_v1"
);
