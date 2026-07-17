-- Demo strategies
INSERT INTO risk_strategy (code, name, scenario, description, enabled, priority) VALUES
('POST_ORDER_DEFAULT', '下单后默认策略', 'POST_ORDER', '通用下单后实时风控策略，覆盖金额、频次、名单等维度', TRUE, 100),
('POST_ORDER_HIGH_VALUE', '高价值订单策略', 'POST_ORDER', '单笔金额超过5000元的加强校验策略', TRUE, 200);

-- Demo rules for POST_ORDER_DEFAULT
INSERT INTO risk_rule (strategy_id, code, name, rule_type, field, operator, threshold, action, score_weight, priority, enabled, description) VALUES
(1, 'R001', '黑名单用户拦截', 'LIST_HIT', 'userId', 'IN', 'BLACKLIST', 'REJECT', 100, 10, TRUE, '命中用户黑名单直接拒绝'),
(1, 'R002', '白名单用户放行', 'LIST_HIT', 'userId', 'IN', 'WHITELIST', 'PASS', 0, 20, TRUE, '命中用户白名单直接放行'),
(1, 'R003', '单笔金额超限', 'THRESHOLD', 'amount', 'GT', '50000', 'REJECT', 80, 30, TRUE, '单笔订单金额超过5万拒绝'),
(1, 'R004', '大额订单人工审核', 'THRESHOLD', 'amount', 'GT', '10000', 'REVIEW', 40, 40, TRUE, '单笔超过1万转人工审核'),
(1, 'R005', '24h下单频次异常', 'THRESHOLD', 'orderCount24h', 'GT', '20', 'REVIEW', 35, 50, TRUE, '24小时内下单超过20笔'),
(1, 'R006', '24h累计金额异常', 'THRESHOLD', 'amountSum24h', 'GT', '100000', 'REVIEW', 45, 60, TRUE, '24小时累计金额超过10万'),
(1, 'R007', '新用户大额订单', 'COMPOSITE', 'isNewUser,amount', 'AND_GT', 'true,5000', 'REVIEW', 50, 70, TRUE, '新用户单笔超过5000元'),
(1, 'R008', '高风险IP段', 'LIST_HIT', 'ip', 'IN', 'IP_BLACKLIST', 'REVIEW', 30, 80, TRUE, '命中IP黑名单转审核'),
(1, 'R009', '境外支付加强校验', 'THRESHOLD', 'country', 'NE', 'CN', 'REVIEW', 25, 90, TRUE, '非中国大陆IP转审核');

-- Demo rules for POST_ORDER_HIGH_VALUE
INSERT INTO risk_rule (strategy_id, code, name, rule_type, field, operator, threshold, action, score_weight, priority, enabled, description) VALUES
(2, 'R101', '高价值强制审核', 'THRESHOLD', 'amount', 'GT', '5000', 'REVIEW', 60, 10, TRUE, '高价值策略下超过5000必审'),
(2, 'R102', '设备指纹异常', 'THRESHOLD', 'deviceRiskScore', 'GT', '70', 'REVIEW', 40, 20, TRUE, '设备风险分超过70');

-- Demo lists
INSERT INTO risk_list_entry (list_type, list_key, list_value, reason, source, enabled) VALUES
('BLACKLIST', 'userId', 'U999999', '历史欺诈用户', 'MANUAL', TRUE),
('BLACKLIST', 'userId', 'U888888', '盗卡嫌疑', 'SYSTEM', TRUE),
('WHITELIST', 'userId', 'U100001', 'VIP企业客户', 'MANUAL', TRUE),
('WHITELIST', 'userId', 'U100002', '内部测试账号', 'MANUAL', TRUE),
('IP_BLACKLIST', 'ip', '192.168.99.100', '代理IP池', 'SYSTEM', TRUE),
('IP_BLACKLIST', 'ip', '10.0.0.55', '异常登录IP', 'SYSTEM', TRUE);

-- Demo decision logs（含可解释字段：reasons / versions / featureSnapshot）
INSERT INTO risk_decision_log (
  request_id, order_id, user_id, scenario, strategy_code, decision, risk_score, risk_level,
  hit_rules, reason, amount, ip, device_id, latency_ms, source,
  trace_id, fixture_id, operator_id, market_json, stake_cents, odds, payout_cents,
  max_acceptable_stake_cents, reasons_json, versions_json, feature_snapshot_json, created_at
) VALUES
('REQ-001', 'ORD-20250525001', 'U100001', 'POST_ORDER', 'POST_ORDER_DEFAULT', 'PASS', 5, 'LOW',
 '[]', '白名单用户直接放行', 299.00, '113.88.1.1', 'DEV-A001', 12, 'SYNC',
 'tr-001', NULL, 'OP-A001', NULL, 29900, NULL, NULL, NULL,
 '[]',
 '{"configEpoch":42,"paramSetVersion":"ps-v7","ruleSetVersion":"rs-v12","engineBuild":"2026.07.15-a1b2c3"}',
 '{"orderCount24h":2,"amountSum24h":598}',
 TIMESTAMPADD(HOUR, -2, CURRENT_TIMESTAMP)),

('REQ-002', 'ORD-20250525002', 'U200001', 'POST_ORDER', 'POST_ORDER_DEFAULT', 'REVIEW', 65, 'MEDIUM',
 '["R004","R005"]', '大额订单且24h频次偏高', 15800.00, '58.220.1.10', 'DEV-B002', 18, 'SYNC',
 'tr-002', NULL, 'OP-A001', NULL, 1580000, NULL, NULL, NULL,
 '[{"ruleId":"R004","ruleVersion":1,"stage":"USER","action":"REVIEW","message":"单笔超过1万转人工审核","evidence":{"amount":15800}},{"ruleId":"R005","ruleVersion":1,"stage":"USER","action":"REVIEW","message":"24小时内下单超过20笔","evidence":{"orderCount24h":28}}]',
 '{"configEpoch":42,"paramSetVersion":"ps-v7","ruleSetVersion":"rs-v12","engineBuild":"2026.07.15-a1b2c3"}',
 '{"orderCount24h":28,"amountSum24h":86000,"isNewUser":false}',
 TIMESTAMPADD(HOUR, -1, CURRENT_TIMESTAMP)),

('REQ-003', 'ORD-20250525003', 'U999999', 'POST_ORDER', 'POST_ORDER_DEFAULT', 'REJECT', 100, 'HIGH',
 '["R001"]', '命中用户黑名单', 1200.00, '192.168.99.100', 'DEV-C003', 8, 'MOCK',
 'tr-003', NULL, 'OP-A001', NULL, 120000, NULL, NULL, NULL,
 '[{"ruleId":"R001","ruleVersion":1,"stage":"USER","action":"REJECT","message":"命中用户黑名单直接拒绝","evidence":{"listCode":"USER_BLACK","userId":"U999999"}}]',
 '{"configEpoch":42,"paramSetVersion":"ps-v7","ruleSetVersion":"rs-v12","engineBuild":"2026.07.15-a1b2c3"}',
 '{"orderCount24h":1,"amountSum24h":1200}',
 TIMESTAMPADD(MINUTE, -30, CURRENT_TIMESTAMP)),

('REQ-004', 'ORD-20250525004', 'U300001', 'POST_ORDER', 'POST_ORDER_DEFAULT', 'PASS', 15, 'LOW',
 '[]', '综合评分通过', 899.00, '114.25.3.8', 'DEV-D004', 15, 'SYNC',
 'tr-004', NULL, 'OP-A001', NULL, 89900, NULL, NULL, NULL,
 '[]',
 '{"configEpoch":42,"paramSetVersion":"ps-v7","ruleSetVersion":"rs-v12","engineBuild":"2026.07.15-a1b2c3"}',
 '{"orderCount24h":3,"amountSum24h":2400}',
 TIMESTAMPADD(MINUTE, -15, CURRENT_TIMESTAMP)),

('REQ-005', 'ORD-20250525005', 'U400001', 'POST_ORDER', 'POST_ORDER_HIGH_VALUE', 'REVIEW', 72, 'HIGH',
 '["R101","R102"]', '高价值策略命中', 8800.00, '10.0.0.55', 'DEV-E005', 22, 'API',
 'tr-005', NULL, 'OP-B002', NULL, 880000, NULL, NULL, NULL,
 '[{"ruleId":"R101","ruleVersion":1,"stage":"USER","action":"REVIEW","message":"高价值策略下超过5000必审","evidence":{"amount":8800}},{"ruleId":"R102","ruleVersion":1,"stage":"USER","action":"REVIEW","message":"设备风险分超过70","evidence":{"deviceRiskScore":82}}]',
 '{"configEpoch":42,"paramSetVersion":"ps-v7","ruleSetVersion":"rs-v12","engineBuild":"2026.07.15-a1b2c3"}',
 '{"orderCount24h":4,"amountSum24h":22000,"deviceRiskScore":82}',
 TIMESTAMPADD(MINUTE, -5, CURRENT_TIMESTAMP)),

('REQ-006', 'ORD-20260715001', 'U555001', 'SPORTS_BET', 'SPORTS_LIMIT', 'REJECT', 90, 'HIGH',
 '["R_LIMIT_PROPORTIONAL"]', '本单返彩超过盘口可接上限', 5000.00, '1.2.3.4', 'DEV-S001', 6, 'FLINK',
 'tr-7f3a9c', 'MATCH-001', 'OP-A001',
 '{"playType":"MatchResult","marketFamily":"ONE_X_TWO","line":"","selection":"HOME"}',
 500000, '2.100', 1050000, NULL,
 '[{"ruleId":"R_LIMIT_PROPORTIONAL","ruleVersion":2,"stage":"GATE1_LIMIT","action":"REJECT","message":"本单返彩 10500.00 元 ≥ 盘口可接上限 4596.70 元（1X2 组，δ=0.2，w=1/3）","evidence":{"bMaxCents":459670,"groupPayoutCents":{"HOME":2800000,"DRAW":600000,"AWAY":900000},"seedCents":200000,"delta":0.2,"weight":"1/3","boundary":"GTE"}}]',
 '{"configEpoch":42,"paramSetVersion":"ps-v7","ruleSetVersion":"rs-v12","engineBuild":"2026.07.15-a1b2c3"}',
 '{"confirmedOrders":52,"pendingReserved":0,"worstLossCents":-96000,"worstScore":"1:0","liveScore":null,"gridSpec":"0-5x0-5","duplicateIgnored":false}',
 TIMESTAMPADD(MINUTE, -3, CURRENT_TIMESTAMP)),

('REQ-007', 'ORD-20260715002', 'U555002', 'SPORTS_BET', 'SPORTS_LIMIT', 'LIMIT', 55, 'MEDIUM',
 '["R_LIMIT_PROPORTIONAL"]', '本单超过可接上限，存在部分可接额度', 3000.00, '1.2.3.5', 'DEV-S002', 7, 'FLINK',
 'tr-9c44aa', 'MATCH-002', 'OP-B002',
 '{"playType":"AsianHandicap","marketFamily":"HANDICAP","line":"-0.75","selection":"HOME"}',
 300000, '1.900', 570000, 121000,
 '[{"ruleId":"R_LIMIT_PROPORTIONAL","ruleVersion":2,"stage":"GATE1_LIMIT","action":"LIMIT","message":"本单返彩 5700.00 元超过可接上限 2299.90 元，本单最多可接本金 1210.00 元","evidence":{"bMaxCents":229990,"groupPayoutCents":{"HOME":800000,"AWAY":400000},"seedCents":200000,"delta":0.2,"weight":"1/2"}}]',
 '{"configEpoch":42,"paramSetVersion":"ps-v7","ruleSetVersion":"rs-v12","engineBuild":"2026.07.15-a1b2c3"}',
 '{"confirmedOrders":12,"pendingReserved":0,"worstLossCents":-31000,"worstScore":"0:2","liveScore":null,"gridSpec":"0-5x0-5","duplicateIgnored":false}',
 TIMESTAMPADD(MINUTE, -2, CURRENT_TIMESTAMP)),

('REQ-008', 'ORD-20260715003', 'U555003', 'SPORTS_BET', 'SPORTS_LIMIT', 'PASS', 10, 'LOW',
 '[]', '限额与敞口均通过', 1000.00, '1.2.3.6', 'DEV-S003', 8, 'FLINK',
 'tr-pass01', 'MATCH-001', 'OP-A001',
 '{"playType":"OverUnder","marketFamily":"OVER_UNDER","line":"2.5","selection":"OVER"}',
 100000, '1.950', 195000, NULL,
 '[]',
 '{"configEpoch":42,"paramSetVersion":"ps-v7","ruleSetVersion":"rs-v12","engineBuild":"2026.07.15-a1b2c3"}',
 '{"confirmedOrders":37,"pendingReserved":1,"worstLossCents":-84000,"worstScore":"2:1","liveScore":"1:0","gridSpec":"1-6x0-5","duplicateIgnored":false,"gate1":{"bMaxCents":483500,"groupPayoutCents":{"OVER":1200000,"UNDER":900000},"seedCents":200000}}',
 TIMESTAMPADD(MINUTE, -1, CURRENT_TIMESTAMP));

-- Demo cases
INSERT INTO risk_case (case_no, decision_log_id, order_id, user_id, operator_id, status, priority, risk_score, risk_level, sla_deadline, callback_status, created_at) VALUES
('CASE-20250525001', 2, 'ORD-20250525002', 'U200001', 'OP-A001', 'PENDING', 'HIGH', 65, 'MEDIUM', TIMESTAMPADD(HOUR, 4, CURRENT_TIMESTAMP), 'NONE', TIMESTAMPADD(HOUR, -1, CURRENT_TIMESTAMP)),
('CASE-20250525002', 5, 'ORD-20250525005', 'U400001', 'OP-A001', 'PENDING', 'URGENT', 72, 'HIGH', TIMESTAMPADD(HOUR, 2, CURRENT_TIMESTAMP), 'NONE', TIMESTAMPADD(MINUTE, -5, CURRENT_TIMESTAMP));

-- Demo events
INSERT INTO risk_event (event_type, severity, order_id, user_id, title, detail, created_at) VALUES
('DECISION', 'INFO', 'ORD-20250525001', 'U100001', '订单风控通过', '白名单用户，风险分5', TIMESTAMPADD(HOUR, -2, CURRENT_TIMESTAMP)),
('DECISION', 'WARN', 'ORD-20250525002', 'U200001', '订单转人工审核', '命中规则: R004,R005', TIMESTAMPADD(HOUR, -1, CURRENT_TIMESTAMP)),
('DECISION', 'ERROR', 'ORD-20250525003', 'U999999', '订单被拒绝', '命中黑名单规则 R001', TIMESTAMPADD(MINUTE, -30, CURRENT_TIMESTAMP)),
('CASE_CREATED', 'WARN', 'ORD-20250525002', 'U200001', '审核工单创建', 'CASE-20250525001', TIMESTAMPADD(HOUR, -1, CURRENT_TIMESTAMP)),
('LIST_HIT', 'WARN', NULL, 'U999999', '黑名单命中', '用户 U999999 尝试下单', TIMESTAMPADD(MINUTE, -30, CURRENT_TIMESTAMP)),
('DECISION', 'ERROR', 'ORD-20260715001', 'U555001', '限额闸门拒单', 'R_LIMIT_PROPORTIONAL Gate1', TIMESTAMPADD(MINUTE, -3, CURRENT_TIMESTAMP)),
('DECISION', 'WARN', 'ORD-20260715002', 'U555002', '限额部分可接', 'LIMIT maxAcceptable=1210', TIMESTAMPADD(MINUTE, -2, CURRENT_TIMESTAMP)),
('CONFIG_PUBLISH', 'INFO', NULL, 'admin', '配置已发布', 'epoch=42 → girisk.config.v1', TIMESTAMPADD(HOUR, -6, CURRENT_TIMESTAMP));

-- Demo sports matches
INSERT INTO sports_match (match_code, home_team, away_team, sport_code, league_code, league_name, exposure_threshold, limit_mode, current_exposure, delta, status) VALUES
('MATCH-001', '曼城', '利物浦', 'football', 'EPL', '英超', 12000, FALSE, 15000, 0.2, 'ACTIVE'),
('MATCH-002', '皇马', '巴萨', 'football', 'LALIGA', '西甲', 80000, FALSE, 1700.12, 0.2, 'ACTIVE'),
('MATCH-003', '拜仁', '多特', 'football', 'BUNDESLIGA', '德甲', 50000, FALSE, 8200, 0.2, 'ACTIVE'),
('MATCH-004', '湖人', '勇士', 'basketball', 'NBA', 'NBA', 60000, FALSE, 12000, 0.2, 'ACTIVE'),
('MATCH-005', '切尔西', '阿森纳', 'football', 'EPL', '英超', 50000, TRUE, 42000, 0.2, 'ACTIVE');

-- 配置发布历史
INSERT INTO risk_config_release (
  config_epoch, scope, status, param_set_version, rule_set_version,
  param_set_json, rule_set_json, change_summary, created_by,
  submitted_by, approved_by, published_by, approval_ticket,
  created_at, submitted_at, approved_at, published_at
) VALUES
(41, 'global', 'PUBLISHED', 'ps-v6', 'rs-v11',
 '{"version":"ps-v6","limit":{"delta":0.2,"basis":"payout","initialSeedPayoutCents":200000,"rejectBoundary":"GTE"},"exposure":{"maxWorstLossCents":100000},"decision":{"limitDecisionEnabled":false,"unknownPlayTypePolicy":"REVIEW","pendingReserveTtlMs":30000}}',
 '{"version":"rs-v11","rules":[{"ruleId":"R_USER_BLACKLIST","ruleVersion":2,"stage":"USER","type":"LIST_HIT","action":"REJECT","priority":1}]}',
 '初始生产参数：限额恒开关闭 LIMIT 决策', 'admin',
 'admin', 'reviewer', 'admin', 'RISK-2026-0701-01',
 TIMESTAMPADD(DAY, -14, CURRENT_TIMESTAMP), TIMESTAMPADD(DAY, -14, CURRENT_TIMESTAMP),
 TIMESTAMPADD(DAY, -13, CURRENT_TIMESTAMP), TIMESTAMPADD(DAY, -13, CURRENT_TIMESTAMP)),

(42, 'global', 'PUBLISHED', 'ps-v7', 'rs-v12',
 '{"version":"ps-v7","limit":{"delta":0.2,"basis":"payout","initialSeedPayoutCents":200000,"rejectBoundary":"GTE"},"exposure":{"maxWorstLossCents":100000,"grid":{"home":6,"away":6,"liveScoreDynamic":true}},"decision":{"limitDecisionEnabled":true,"unknownPlayTypePolicy":"REVIEW","pendingReserveTtlMs":30000}}',
 '{"version":"rs-v12","rules":[{"ruleId":"R_USER_BLACKLIST","ruleVersion":3,"stage":"USER","type":"LIST_HIT","action":"REJECT","priority":1},{"ruleId":"R_USER_STAKE_5M","ruleVersion":2,"stage":"USER","type":"THRESHOLD","field":"stake_sum_5m_cents","op":"GT","value":10000000,"action":"REVIEW","priority":10}]}',
 '启用 LIMIT 部分可接；新增用户 5 分钟限额规则', 'admin',
 'admin', 'reviewer', 'admin', 'RISK-2026-0715-01',
 TIMESTAMPADD(HOUR, -8, CURRENT_TIMESTAMP), TIMESTAMPADD(HOUR, -7, CURRENT_TIMESTAMP),
 TIMESTAMPADD(HOUR, -6, CURRENT_TIMESTAMP), TIMESTAMPADD(HOUR, -6, CURRENT_TIMESTAMP)),

(43, 'global', 'PENDING_APPROVAL', 'ps-v8', 'rs-v12',
 '{"version":"ps-v8","limit":{"delta":0.15,"basis":"payout","initialSeedPayoutCents":300000,"rejectBoundary":"GTE"},"exposure":{"maxWorstLossCents":150000,"grid":{"home":6,"away":6,"liveScoreDynamic":true}},"decision":{"limitDecisionEnabled":true,"unknownPlayTypePolicy":"REVIEW","pendingReserveTtlMs":30000}}',
 '{"version":"rs-v12","rules":[{"ruleId":"R_USER_BLACKLIST","ruleVersion":3,"stage":"USER","type":"LIST_HIT","action":"REJECT","priority":1},{"ruleId":"R_USER_STAKE_5M","ruleVersion":2,"stage":"USER","type":"THRESHOLD","field":"stake_sum_5m_cents","op":"GT","value":10000000,"action":"REVIEW","priority":10}]}',
 '收紧 δ=0.15，提高种子与敞口阈值', 'admin',
 'admin', NULL, NULL, NULL,
 TIMESTAMPADD(HOUR, -1, CURRENT_TIMESTAMP), TIMESTAMPADD(MINUTE, -40, CURRENT_TIMESTAMP),
 NULL, NULL);

-- 场次风险物化视图（与 sports_match.match_code 对齐，便于下钻）
INSERT INTO risk_fixture_view (
  fixture_id, home_team, away_team, operator_id, confirmed_orders, pending_reserved,
  worst_loss_cents, worst_score, live_score, market_summary_json, risk_level, updated_at
) VALUES
('MATCH-001', '曼城', '利物浦', 'OP-A001', 52, 1, 96000, '1:0', '1:0',
 '{"ONE_X_TWO":{"HOME":2800000,"DRAW":600000,"AWAY":900000},"OVER_UNDER:2.5":{"OVER":1200000,"UNDER":900000}}',
 'HIGH', CURRENT_TIMESTAMP),
('MATCH-002', '皇马', '巴萨', 'OP-B002', 12, 0, 31000, '0:2', NULL,
 '{"HANDICAP:-0.75":{"HOME":800000,"AWAY":400000}}',
 'MEDIUM', CURRENT_TIMESTAMP),
('MATCH-003', '拜仁', '多特', 'OP-A001', 8, 0, 12000, '0:3', NULL,
 '{"ONE_X_TWO":{"HOME":400000,"DRAW":200000,"AWAY":350000}}',
 'LOW', CURRENT_TIMESTAMP),
('MATCH-005', '切尔西', '阿森纳', 'OP-A001', 28, 2, 128000, '2:1', '1:1',
 '{"ONE_X_TWO":{"HOME":3100000,"DRAW":800000,"AWAY":1100000}}',
 'CRITICAL', CURRENT_TIMESTAMP);
