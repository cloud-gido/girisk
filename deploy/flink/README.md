# GiRisk Engine 部署（Flink）

主类：`com.girisk.flink.risk.FootballOrderKafkaJob`

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
| `--sink.topic.decision` | girisk.decision.v1 | 唯一决策出口 |
| `--sink.decision.enabled` | true | 写出决策 |
| `--sink.redis.view.enabled` | false | 写 Redis 大盘视图 |
| `--sink.redis.host` / `--sink.redis.port` | 127.0.0.1:6379 | Redis |
| `--pending.reserve.ttlMs` | 30000 | PENDING 预留 TTL |
| `--limit.delta` | 0.2 | 等比例 δ |
| `--exposure.maxWorstLossYuan` | 1000 | Gate2 阈值 |

K8s 清单可参考本目录下 `local/`、`prod/`（自原 flink-kafka-print 迁入，提交前请按集群改镜像与 bootstrap）。
