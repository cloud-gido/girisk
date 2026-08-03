# GiRisk Doris 审计层

链路：`Engine / Console / 交易 → Kafka → Doris Routine Load → 三表`

| 文件 | 内容 |
|------|------|
| `01_create_tables.sql` | `risk_decision_log` / `risk_config_log` / `risk_order_status_log` |
| `02_routine_load.sql` | 生产 broker 模板（替换 `REPLACE_WITH_KAFKA_BROKERS`） |
| `../deploy/doris/02_routine_load.docker.sql` | 本地 compose：`kafka:9092` |

设计对齐 giso：高频字段拉平做过滤；`raw STRING` 存完整 Kafka 原文，未映射字段用 `get_json_*` 补查。

## Topic → 表 → consumer group

| Topic | 表 | group.id |
|-------|-----|----------|
| `girisk.decision.v1` | `risk_decision_log` | `doris_girisk_decision_v1` |
| `girisk.config.v1` | `risk_config_log` | `doris_girisk_config_v1` |
| `girisk.trading.order.risk-check.post.v1` | `risk_order_status_log` | `doris_girisk_order_status_v1` |

与 Console 运营消费（REVIEW 建单）使用**不同** group，互不影响。

## 本地 Docker

优先复用本机 **giso** 已有镜像，避免重复 pull / build：

| 服务 | 镜像 |
|------|------|
| FE | `apache/doris:fe-ubuntu-2.1.7` |
| BE | `deploy-doris-be:latest`（giso patched） |
| init | `deploy-doris-init:latest` + 挂载本仓 `init.sh` |
| Kafka | `apache/kafka:3.9.0`（与 giso 同） |

```bash
# MySQL + Kafka + Redis + Doris FE/BE + 建表 + Routine Load
docker compose --profile doris up -d

# 验证
./scripts/verify-doris-audit.sh
```

FE MySQL 协议：`localhost:9030`（root / 无密码）。FE UI：`http://localhost:8030`。  
宿主机连 Kafka 用 **`127.0.0.1:9094`**（EXTERNAL）；容器内 / Routine Load 用 `kafka:9092`。

## 生产接入

```bash
mysql -h <doris-fe> -P9030 -uroot < doris/01_create_tables.sql
# 编辑 02_routine_load.sql 中 broker 后：
mysql -h <doris-fe> -P9030 -uroot < doris/02_routine_load.sql
mysql -h <doris-fe> -P9030 -uroot -e "USE girisk; SHOW ROUTINE LOAD;"
```

默认 `OFFSET_END`（只要增量）。全量重灌：先按最早 `decision_date` 补齐 RANGE 分区，再改 `OFFSET_BEGINNING` 与新 `group.id`。

## 排障：`no partition for this tuple` → Load PAUSED

与 giso 相同根因：历史消息日期无分区 → 错误堆积 → PAUSED。

```sql
USE girisk;
SHOW ROUTINE LOAD FOR load_risk_decision\G
SHOW PARTITIONS FROM risk_decision_log;

ALTER TABLE risk_decision_log SET ("dynamic_partition.enable" = "false");
ALTER TABLE risk_decision_log
ADD PARTITIONS FROM ("2026-06-01") TO ("2026-07-20") INTERVAL 1 DAY;
ALTER TABLE risk_decision_log SET ("dynamic_partition.enable" = "true");
RESUME ROUTINE LOAD FOR load_risk_decision;
```

## Console 回放

运营台读 Doris（可选）：

```yaml
girisk.audit.doris.enabled: true
girisk.audit.doris.jdbc-url: jdbc:mysql://localhost:9030/girisk
girisk.audit.doris.username: root
girisk.audit.doris.password: ""
```

未启用或查询失败时回退 MySQL `risk_decision_log`（REVIEW / 值班仍走 MySQL）。详见 [docs/AUDIT.md](../docs/AUDIT.md)。
