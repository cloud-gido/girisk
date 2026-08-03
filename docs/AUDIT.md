# GiRisk 审计闭环

目标（方案 §8.2 / §11.1）：决策 / 配置 / 状态三条 Kafka 消息**原样**入 Doris；运营台按 orderId / traceId 回放时优先读 Doris，仅凭消息本体解释「为什么拒 / 为什么放」。

## 架构分工

| 平面 | 存储 | 职责 |
|------|------|------|
| 审计平面 | **Doris**（Routine Load） | `risk_decision_log` / `risk_config_log` / `risk_order_status_log`；长期保留、回放权威 |
| 运营平面 | **PostgreSQL 16 / H2**（Console） | REVIEW 建单、值班统计、配置草稿审批；消费 `decision.v1` 的独立 group |

二者 **consumer group 分离**，Doris 写入失败不影响裁决与工单。

### 三平面数字为何可能不一致

| 平面 | 写入 | 去重 | 同单多次 Kafka 重放 |
|------|------|------|---------------------|
| Doris | Routine Load 追加 | **无**（`DUPLICATE KEY`） | N 行（保留证据） |
| Redis 接收/拦截/重复 | `ReplayStatsSink` | **按 orderId**（`girisk:idem:fixture-decision:{fixtureId}`） | 只计 **1** 次 |
| Console PG | `FlinkDecisionIngressConsumer` | `requestId` / `traceId` | 只留 **1** 行 |

对账时 Doris 建议取最新：

```sql
SELECT *
FROM (
  SELECT *,
         ROW_NUMBER() OVER (PARTITION BY trace_id ORDER BY decision_time DESC) AS rn
  FROM bigdata_ods.ods_gameline_risk_decision_log
  WHERE fixture_id = '14219242'
) t
WHERE rn = 1;
```

```text
girisk.decision.v1 ──► Routine Load ──► Doris risk_decision_log
                   └──► Console group ──► PostgreSQL + risk_case

girisk.config.v1   ──► Routine Load ──► Doris risk_config_log
                   └──► Flink broadcast

girisk.trading.order.risk-check.post.v1
                   ──► Routine Load ──► Doris risk_order_status_log
                   └──► Flink post-feedback
```

## 本地启用

```bash
# 复用本机 giso Doris/Kafka 镜像，见 docker-compose.yml 注释
docker compose --profile doris up -d
./scripts/verify-doris-audit.sh
```

Console 回放读 Doris（宿主机进程）：

```bash
export GIRISK_AUDIT_DORIS_ENABLED=true
export KAFKA_BOOTSTRAP=127.0.0.1:9094
export KAFKA_ENABLED=true
export REDIS_ENABLED=true
./start.sh --postgres --background
```

或在 `application.yml`：

```yaml
girisk:
  audit:
    doris:
      enabled: true
      jdbc-url: jdbc:mysql://localhost:9030/girisk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
      username: root
      password: ""
```

回放 API `GET /api/v1/replay/order/{orderId}` 响应字段 `auditSource`：`doris` | `postgres`。Doris 无行或连接失败时自动回退 PostgreSQL 运营库。

## SQL 与 Routine Load

见 [doris/README.md](../doris/README.md)：

- [`doris/01_create_tables.sql`](../doris/01_create_tables.sql)
- [`doris/02_routine_load.sql`](../doris/02_routine_load.sql)（生产 broker 模板）
- [`deploy/doris/02_routine_load.docker.sql`](../deploy/doris/02_routine_load.docker.sql)

设计：高频列拉平 + `raw STRING` 全文；动态分区（决策/状态 ≥2 年，配置约 10 年）。本地默认 `OFFSET_END`。

## 决策消息（审计友好字段）

Engine `RiskDecisionJson` 在 `girisk.decision.v1` 中输出（节选）：

| 字段 | 用途 |
|------|------|
| `decisionTimeMs` | 分区与时间列 |
| `orderId` / `traceId` / `operatorId` / `fixtureId` | 回放键 |
| `decision` / `reasons` / `versions` / `evidence` | 可解释 |
| `stakeCents` / `odds` / `payoutCents` | 拉平列 |
| `evidence.gate1TriggerSelection` | Gate1：`b_max` / 目标 / 最大允许 / 判断前盘口占用 |
| `featureSnapshot.beforeAccept` / `trialAfterAccept` / `afterActual` | Gate2：累计投注、最差比分/盈亏 |
| `productAudit` | 产品对账中文列（限额公式、拦截类型、Genius 占位等）；全文亦在 Doris `raw` |
| `versions.configEpoch` | 关联 `risk_config_log`（未接 config 时为 null） |

`Genius判断结果` 本仓库无 Genius 对接，固定为 `null`。

## Console 运营面审计（PostgreSQL）

消费 `girisk.decision.v1`（group=`girisk-console-flink-decision`）时：

| 行为 | `risk_event.event_type` | 说明 |
|------|-------------------------|------|
| 入库成功 | `FLINK_DECISION` | detail 含 decisionId / 闸门位 / stake / odds |
| 幂等跳过 | `DECISION_INGEST_DUP` | 同 `requestId`/`traceId` 已存在 |
| REVIEW 建单 | `FLINK_REVIEW_CASE` | 关联 caseNo |
| 入库失败（每次） | `DECISION_INGEST_FAIL` | 含错误与 raw 截断 |
| 重试耗尽跳过 | `DECISION_INGEST_DEAD` | 避免毒消息卡死消费组 |

`evidence_json` 会合并 `productAudit`，回放不丢产品对账字段。

**抗踢配置（默认已开）：** Redis 超时 1s；Kafka `max.poll.records=50`、`max.poll.interval.ms=300000`；定时任务独立线程池；入库失败有限次重试后跳过。

## 验收清单

1. `SHOW ROUTINE LOAD` 三任务 `State=RUNNING`
2. 向三 topic 投样例 → Doris 有行且 `raw` 可解析
3. `GIRISK_AUDIT_DORIS_ENABLED=true` 时回放返回 `auditSource=doris`
4. 未启 Doris 时回放仍走 PostgreSQL，exposure-demo 不受影响
5. Console 消费决策后 `risk_event` 有 `FLINK_DECISION`；重复投递出现 `DECISION_INGEST_DUP`
