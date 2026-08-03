-- 本地 Docker：Kafka broker 使用 compose 服务名
USE girisk;

CREATE ROUTINE LOAD girisk.load_risk_decision ON risk_decision_log
COLUMNS(
    decision_time_ms, order_id, fixture_id, operator_id, user_id, trace_id,
    decision, engine_build, config_epoch, stake_cents, odds, payout_cents,
    reasons, versions, evidence, feature_snapshot, raw,
    decision_time = from_unixtime(IFNULL(decision_time_ms, unix_timestamp()) / 1000),
    decision_date = to_date(from_unixtime(IFNULL(decision_time_ms, unix_timestamp()) / 1000)),
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
    "kafka_broker_list" = "kafka:9092",
    "kafka_topic" = "girisk.decision.v1",
    "property.kafka_default_offsets" = "OFFSET_END",
    "property.group.id" = "doris_girisk_decision_v1"
);

CREATE ROUTINE LOAD girisk.load_risk_config ON risk_config_log
COLUMNS(
    config_epoch, scope, approval_ticket, published_by, published_at_str,
    param_set_json, rule_set_json, raw,
    published_at = now(),
    publish_date = curdate(),
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
    "kafka_broker_list" = "kafka:9092",
    "kafka_topic" = "girisk.config.v1",
    "property.kafka_default_offsets" = "OFFSET_END",
    "property.group.id" = "doris_girisk_config_v1"
);

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
    "kafka_broker_list" = "kafka:9092",
    "kafka_topic" = "girisk.trading.order.risk-check.post.v1",
    "property.kafka_default_offsets" = "OFFSET_END",
    "property.group.id" = "doris_girisk_order_status_v1"
);
