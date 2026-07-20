# GiRisk Engine 部署（Flink）

主类：`com.girisk.flink.risk.FootballOrderKafkaJob`

**推荐生产路径：** 打 shade jar → 用 **gido 实时作业**（JAR）提交到已有 Flink 集群（运行时镜像 `gido-flink-runtime`）。本仓 **不** 默认构建 / 推送 Engine Docker 镜像；Console 才走 GHCR（见 [../README.md](../README.md)）。

## IDEA 本地跑 main（常见踩坑）

`flink-streaming-java` / `flink-clients` 是 **`provided`**，IDEA 默认不进运行 classpath，会报：

`NoClassDefFoundError: org/apache/flink/api/common/serialization/DeserializationSchema`

任选其一：

1. **Run Configuration** → Modify options → 勾选 **Add dependencies with "Provided" scope to classpath**（或 Include dependencies with “Provided” scope）
2. Maven 勾选 profile **`ide`** 后 Reload：`mvn -pl girisk-engine -Pide -am compile`，再用 IDEA 跑 main
3. 不跑 IDEA main，改用下面 `flink run` / 本地 jar

Program arguments 示例（compose Kafka 对外 9094）：

```text
--bootstrap 127.0.0.1:9094
--source.accept.csv true
--source.post.enabled false
--live.score.enabled false
--checkpoint.enabled false
--sink.decision.enabled true
--sink.print.decision true
--sink.redis.view.enabled true
--limit.delta 0.2
--limit.seedPayoutYuan 5000
--exposure.maxWorstLossYuan 200000
```

要点：

- **发 CSV 必须加 `--source.accept.csv true`**，否则解析静默丢弃，decision 为空
- 本地 IDEA：建议关掉 checkpoint / 先关 post 与 liveScore，链路更简单
- 种子参数：`--limit.seedPayoutYuan` 或 `--limit.initialSeedPayoutYuan`

## 构建

```bash
cd ../..
mvn -pl girisk-engine -am -DskipTests package
```

产物：`girisk-engine/target/girisk-engine-1.0.0.jar`

## 关键参数

| 参数 | 默认 | 说明 |
|------|------|------|
| `--bootstrap` | localhost:9092 | Kafka |
| `--sink.topic.decision` | girisk.decision.v1 | **唯一**决策/审计出口（含 market / evidence / featureSnapshot / productAudit） |
| `--sink.decision.enabled` | true | 写出决策 |
| `--sink.topic.detail/summary/limit/business` | （空） | 旧四出口，默认不写；显式传 topic 名才打开 |
| `--sink.redis.view.enabled` | **true** | 写 Redis 大盘视图（敞口看板） |
| `--sink.redis.host` / `--sink.redis.port` | 127.0.0.1:6379 | Redis |
| `--config.enabled` | true | 消费 `girisk.config.v1` 热更新门控/限额 |
| `--pending.reserve.ttlMs` | 30000 | PENDING 预留 TTL |
| `--limit.delta` | 0.2 | 等比例 δ（CLI 兜底） |
| `--limit.seedPayoutYuan` | 2000 | 冷启动种子 |
| `--limit.maxBetPayoutYuan` | 0 | Gate0 单注上限（0=关；可被 config.v1 覆盖） |
| `--exposure.maxWorstLossYuan` | 1000 | Gate2 阈值 |

K8s 清单可参考本目录下 `local/`、`prod/`（自原 flink-kafka-print 迁入，提交前请按集群改镜像与 bootstrap）。
