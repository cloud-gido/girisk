-- Risk Platform Schema (PostgreSQL 16；H2 local 请用 MODE=PostgreSQL)

CREATE TABLE IF NOT EXISTS risk_strategy (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL UNIQUE,
    name        VARCHAR(128) NOT NULL,
    scenario    VARCHAR(64)  NOT NULL,
    description VARCHAR(512),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    priority    INT          NOT NULL DEFAULT 100,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_rule (
    id           BIGSERIAL PRIMARY KEY,
    strategy_id  BIGINT       NOT NULL,
    code         VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    rule_type    VARCHAR(32)  NOT NULL,
    field        VARCHAR(64),
    operator     VARCHAR(16),
    threshold    VARCHAR(128),
    action       VARCHAR(32)  NOT NULL,
    score_weight INT          NOT NULL DEFAULT 10,
    priority     INT          NOT NULL DEFAULT 100,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    description  VARCHAR(512),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_list_entry (
    id          BIGSERIAL PRIMARY KEY,
    list_type   VARCHAR(16)  NOT NULL,
    list_key    VARCHAR(64)  NOT NULL,
    list_value  VARCHAR(256) NOT NULL,
    reason      VARCHAR(512),
    source      VARCHAR(64)  DEFAULT 'MANUAL',
    expires_at  TIMESTAMP,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_decision_log (
    id              BIGSERIAL PRIMARY KEY,
    request_id      VARCHAR(64)  NOT NULL,
    order_id        VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(64)  NOT NULL,
    scenario        VARCHAR(64)  NOT NULL,
    strategy_code   VARCHAR(64),
    decision        VARCHAR(32)  NOT NULL,
    risk_score      INT          NOT NULL DEFAULT 0,
    risk_level      VARCHAR(16)  NOT NULL,
    hit_rules       VARCHAR(2048),
    reason          VARCHAR(1024),
    amount          DECIMAL(18,2),
    ip              VARCHAR(64),
    device_id       VARCHAR(128),
    latency_ms      INT,
    source          VARCHAR(32)  NOT NULL DEFAULT 'SYNC',
    trace_id        VARCHAR(64),
    fixture_id      VARCHAR(64),
    operator_id     VARCHAR(64),
    market_json     VARCHAR(1024),
    stake_cents     BIGINT,
    odds            VARCHAR(32),
    payout_cents    BIGINT,
    max_acceptable_stake_cents BIGINT,
    reasons_json    TEXT,
    versions_json   VARCHAR(1024),
    feature_snapshot_json TEXT,
    evidence_json   TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(128) NOT NULL,
    display_name    VARCHAR(64)  NOT NULL,
    role            VARCHAR(32)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    operator_scope  VARCHAR(512) NOT NULL DEFAULT '*',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 已有库补列（H2/PG 均兼容；失败时由 continue-on-error 忽略）
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS operator_scope VARCHAR(512) DEFAULT '*';

-- RBAC：用户 → 角色 → 权限（sys_user.role 保留为主角色展示/兼容）
CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL UNIQUE,
    name        VARCHAR(64)  NOT NULL,
    builtin     BOOLEAN      NOT NULL DEFAULT TRUE,
    description VARCHAR(256),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_permission (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL UNIQUE,
    name        VARCHAR(128) NOT NULL,
    module      VARCHAR(32)  NOT NULL,
    description VARCHAR(256),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX IF NOT EXISTS idx_user_role_user ON sys_user_role(user_id);
CREATE INDEX IF NOT EXISTS idx_role_perm_role ON sys_role_permission(role_id);

CREATE TABLE IF NOT EXISTS risk_case (
    id              BIGSERIAL PRIMARY KEY,
    case_no         VARCHAR(32)  NOT NULL UNIQUE,
    decision_log_id BIGINT       NOT NULL,
    order_id        VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(64)  NOT NULL,
    operator_id     VARCHAR(64),
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    priority        VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
    risk_score      INT          NOT NULL DEFAULT 0,
    risk_level      VARCHAR(16)  NOT NULL,
    assignee        VARCHAR(64),
    review_decision VARCHAR(32),
    review_comment  VARCHAR(1024),
    sla_deadline    TIMESTAMP,
    callback_status VARCHAR(32)  NOT NULL DEFAULT 'NONE',
    callback_payload VARCHAR(2048),
    callback_at     TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at     TIMESTAMP
);

-- 配置发布（版本化 + 审批）
CREATE TABLE IF NOT EXISTS risk_config_release (
    id              BIGSERIAL PRIMARY KEY,
    config_epoch    BIGINT       NOT NULL UNIQUE,
    scope           VARCHAR(64)  NOT NULL DEFAULT 'global',
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    param_set_version VARCHAR(64) NOT NULL,
    rule_set_version  VARCHAR(64) NOT NULL,
    param_set_json  TEXT         NOT NULL,
    rule_set_json   TEXT         NOT NULL,
    change_summary  VARCHAR(1024),
    created_by      VARCHAR(64)  NOT NULL,
    submitted_by    VARCHAR(64),
    approved_by     VARCHAR(64),
    published_by    VARCHAR(64),
    approval_ticket VARCHAR(128),
    reject_reason   VARCHAR(1024),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at    TIMESTAMP,
    approved_at     TIMESTAMP,
    published_at    TIMESTAMP
);

-- 场次风险物化视图（模拟 Flink → Redis，供运营大盘）
CREATE TABLE IF NOT EXISTS risk_fixture_view (
    id                  BIGSERIAL PRIMARY KEY,
    fixture_id          VARCHAR(64)  NOT NULL UNIQUE,
    home_team           VARCHAR(128) NOT NULL,
    away_team           VARCHAR(128) NOT NULL,
    operator_id         VARCHAR(64),
    confirmed_orders    INT          NOT NULL DEFAULT 0,
    pending_reserved    INT          NOT NULL DEFAULT 0,
    worst_loss_cents    BIGINT       NOT NULL DEFAULT 0,
    worst_score         VARCHAR(16),
    live_score          VARCHAR(16),
    market_summary_json TEXT,
    risk_level          VARCHAR(16)  NOT NULL DEFAULT 'LOW',
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_event (
    id          BIGSERIAL PRIMARY KEY,
    event_type  VARCHAR(64)  NOT NULL,
    severity    VARCHAR(16)  NOT NULL DEFAULT 'INFO',
    order_id    VARCHAR(64),
    user_id     VARCHAR(64),
    title       VARCHAR(256) NOT NULL,
    detail      VARCHAR(2048),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_decision_log_order ON risk_decision_log(order_id);
CREATE INDEX IF NOT EXISTS idx_decision_log_user ON risk_decision_log(user_id);
CREATE INDEX IF NOT EXISTS idx_decision_log_created ON risk_decision_log(created_at);
CREATE INDEX IF NOT EXISTS idx_decision_log_trace ON risk_decision_log(trace_id);
CREATE INDEX IF NOT EXISTS idx_decision_log_request ON risk_decision_log(request_id);
CREATE INDEX IF NOT EXISTS idx_case_status ON risk_case(status);
CREATE INDEX IF NOT EXISTS idx_list_type_value ON risk_list_entry(list_type, list_value);
CREATE INDEX IF NOT EXISTS idx_event_created ON risk_event(created_at);
CREATE INDEX IF NOT EXISTS idx_config_release_status ON risk_config_release(status);
CREATE INDEX IF NOT EXISTS idx_fixture_view_worst ON risk_fixture_view(worst_loss_cents);

-- Sports exposure / proportional limit
CREATE TABLE IF NOT EXISTS sports_match (
    id                  BIGSERIAL PRIMARY KEY,
    match_code          VARCHAR(64)  NOT NULL UNIQUE,
    home_team           VARCHAR(128),
    away_team           VARCHAR(128),
    sport_code          VARCHAR(32)  NOT NULL DEFAULT 'football',
    league_code         VARCHAR(64)  DEFAULT 'UNKNOWN',
    league_name         VARCHAR(128) DEFAULT '未分组联赛',
    exposure_threshold  DECIMAL(18,2) NOT NULL DEFAULT 200000,
    limit_mode          BOOLEAN      NOT NULL DEFAULT FALSE,
    current_exposure    DECIMAL(18,2) NOT NULL DEFAULT 0,
    delta               DECIMAL(5,4) NOT NULL DEFAULT 0.2,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    last_check_at       TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 已有库：元数据允许留白（运营后续在页面补全）
ALTER TABLE sports_match ALTER COLUMN home_team DROP NOT NULL;
ALTER TABLE sports_match ALTER COLUMN away_team DROP NOT NULL;
ALTER TABLE sports_match ALTER COLUMN league_code DROP NOT NULL;
ALTER TABLE sports_match ALTER COLUMN league_name DROP NOT NULL;

CREATE TABLE IF NOT EXISTS sports_bet_log (
    id              BIGSERIAL PRIMARY KEY,
    request_id      VARCHAR(64)  NOT NULL,
    order_id        VARCHAR(64)  NOT NULL UNIQUE,
    match_code      VARCHAR(64)  NOT NULL,
    market_type     VARCHAR(32)  NOT NULL,
    line_value      VARCHAR(16),
    selection       VARCHAR(32)  NOT NULL,
    amount          DECIMAL(18,2) NOT NULL,
    odds            DECIMAL(10,4),
    decision        VARCHAR(16)  NOT NULL,
    max_accept      DECIMAL(18,2),
    limit_mode      BOOLEAN      NOT NULL DEFAULT FALSE,
    reason          VARCHAR(512),
    latency_ms      INT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sports_bet_match ON sports_bet_log(match_code);
CREATE INDEX IF NOT EXISTS idx_sports_bet_created ON sports_bet_log(created_at);
