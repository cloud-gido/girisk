# GiRisk 单体仓说明

本仓库为 **GiRisk**（代号 GIRISK，gido 全家桶）统一风控实现：运营台 + Flink 决策引擎。

品牌约定见 [BRANDING.md](BRANDING.md)。

## 模块

| 模块 | 产品角色 | 运行 |
|------|----------|------|
| `girisk-common` | 共享 Topic / 决策码 | 库 |
| `girisk-console` | GiRisk Console：配置平面、工单、大盘、调试沙箱 | `java -jar` / `./start.sh` |
| `girisk-engine` | GiRisk Engine：限额 + 比分矩阵敞口 + decision.v1 + Redis 视图 | `flink run …` |

## 架构

```
交易 → girisk.trading.order.risk-check.v1
         → GiRisk Engine（girisk-engine）
         → girisk.decision.v1 → 交易 / Console（审计·REVIEW）
         → Redis girisk:view:* → Console 敞口大盘

Console 配置发布 → girisk.config.v1 → Engine
```

## 构建

```bash
# 全量
mvn -q verify

# 仅运营台
mvn -pl girisk-console -am -DskipTests package
./start.sh --local --background

# 仅 Flink 作业
mvn -pl girisk-engine -am package
flink run -c com.girisk.flink.risk.FootballOrderKafkaJob \
  girisk-engine/target/girisk-engine-1.0.0.jar \
  --bootstrap localhost:9092 \
  --sink.decision.enabled true \
  --sink.redis.view.enabled true \
  --sink.redis.host 127.0.0.1
```

部署清单见 [deploy/flink](../deploy/flink)。

原独立仓库 `flink-kafka-print` 中体育风控代码已迁入 `girisk-engine`，请勿再双轨维护。

## 架构落地状态（诚实版）

| 能力 | 状态 |
|------|------|
| 单体仓模块拆分、Flink 代码迁入 | **已完成** |
| Flink 写出 `decision.v1` + PENDING 预留 + Redis 视图 | **代码已完成** |
| 运营台消费 decision / 建 REVIEW / 读 Redis / 发 config | **代码已完成** |
| 本仓体育 HTTP 在线裁决默认关闭 | **已完成**（`--local` 仍可开） |
| 默认无演示数据 | **已完成**（`--demo` 才加载） |
| 产品品牌 GiRisk | **已完成** |
| 本机 Flink 集群 + Kafka 全链路联调验收 | **需你环境跑通**（见 deploy/flink） |

清洁链路数据来源应为：**交易 Kafka → Engine → decision/Redis → Console**，不再依赖 `data-demo.sql` / `SportsDemoDataInitializer`。
