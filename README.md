# GiRisk（玑险）

**GiRisk**（代号 **GIRISK**，中文名 **玑险**）是一套开源的交易风控决策平台，同属 **gido（玑渡）** 产品族。名字取自「璇玑定风险、决策有准绳」——在高并发投注链路里，把**限额、敞口、可解释裁决、值班运营**串成一条可审计、可回放的风控闭环。

它对接交易系统与实时计算：订单经 Kafka 预检进入决策引擎，输出唯一结论；运营台消费决策与物化视图，支撑大盘、工单与参数治理。

---

## 你能用它做什么

### GiRisk Engine（玑险·决）

Flink 实时决策引擎：对互斥盘口做等比例限额（返彩口径 `b_max` + 冷启动种子），用比分矩阵估算最差庄家净亏，超阈值拒单；支持单注返彩上限。裁决写入唯一决策出口 `girisk.decision.v1`（理由、证据、版本三元组），并可物化到 Redis 供大盘读取。

### GiRisk Console（玑险·台）

运营与配置平面：敞口看板（总体 → 球类 → 联赛 → 赛事）、四级限额覆盖与停盘、决策审计与按单回放、REVIEW 工单、规则 / 参数配置发布、投注试算与沙箱。本地可无 Flink 集群跑通演示回放看板。

### GiRisk Common（玑险·契）

共享 Topic、决策码与契约定义，保证 Console / Engine / 交易侧说同一种「风控语言」。

---

## 一条链路长什么样

```
交易下单 → Kafka 风控预检 → GiRisk Engine 裁决
                              ↓
                    girisk.decision.v1（唯一出口）
                              ↓
              交易执行  ·  Console 审计 / REVIEW  ·  Redis 敞口大盘
```

Germany vs Paraguay 同源回放示例：接单 **1964** / 拦截 **767**；无风控最差约 **-772k** → 有风控约 **-19.8k**。

> 生产环境体育裁决权威在 Engine；Console HTTP 试算与演示 profile 用于联调与值班，见文档说明。

---

## 仓库结构

```
girisk-common/     共享 Topic、决策码
girisk-console/    运营台（Spring Boot + React）
girisk-engine/     Flink 作业（Kafka → 决策 / Redis 视图）
docs/              方案、演示、产品说明
scripts/           本地演示脚本
deploy/flink/      Flink 部署清单
```

| 文档 | 内容 |
|------|------|
| [docs/BRANDING.md](docs/BRANDING.md) | 品牌与命名约定 |
| [docs/MONOREPO.md](docs/MONOREPO.md) | 模块与架构 |
| [docs/DEMO-EXPOSURE.md](docs/DEMO-EXPOSURE.md) | 本地敞口演示 |
| [docs/PRODUCT-EXPOSURE.md](docs/PRODUCT-EXPOSURE.md) | 看板信息架构 / 四级限额 |
| [docs/risk-decision-platform-solution.md](docs/risk-decision-platform-solution.md) | 总体方案（v3） |

---

## 快速开始

### 环境

- JDK **21+**
- Maven 3.9+
- Node.js 18+（构建 Console 前端）
- Redis（敞口看板演示）
- （可选）Kafka + Flink 2.x（真链路）

### 运营台 · 敞口演示（推荐先跑这个）

不依赖 Flink 集群：启动时若高危表为空会自动灌入演示数据。

```bash
docker compose up -d redis          # 或本机已有 Redis :6379
./start.sh --exposure-demo --background
```

打开：http://localhost:18088/girisk/exposure  

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | ADMIN |
| reviewer | review123 | REVIEWER |
| viewer | view123 | VIEWER |

也可：`./scripts/demo-germany-exposure.sh`（重放 CSV → Redis → 启 Console）。

### 运营台 · 清洁链路

默认无演示业务数据：

```bash
./start.sh --local --background     # H2
./start.sh --mysql --background     # MySQL
./stop.sh
```

### GiRisk Engine（Flink）

```bash
mvn -pl girisk-engine -am -DskipTests package
flink run -c com.girisk.flink.risk.FootballOrderKafkaJob \
  girisk-engine/target/girisk-engine-1.0.0.jar \
  --bootstrap localhost:9092 \
  --sink.decision.enabled true \
  --sink.redis.view.enabled true \
  --limit.delta 0.2 \
  --limit.seedPayoutYuan 5000 \
  --exposure.maxWorstLossYuan 200000
```

部署说明：[deploy/flink/README.md](deploy/flink/README.md)

---

## 架构（目标）

```
交易 → girisk.trading.order.risk-check.v1
         → GiRisk Engine
         → girisk.decision.v1 → 交易 / Console（审计 · REVIEW）
         → Redis girisk:view:* → Console 敞口大盘

Console 配置 / 层级限额 →（配置面）→ Engine
```

生产路径上，体育在线裁决权威在 **Engine**；Console 的 HTTP decide 默认关闭体育限额（`exposure-demo` / `--local` 可开以便试算）。

---

## 技术栈

| 层 | 技术 |
|----|------|
| Console | Java 21 · Spring Boot 3 · React · Ant Design · Redis · H2/MySQL |
| Engine | Apache Flink · Kafka · Redis |
| 契约 | `girisk.*` Topics · 单一决策出口 `girisk.decision.v1` |

---

## 状态说明（诚实版）

| 能力 | 状态 |
|------|------|
| 单体仓 · Console / Engine 模块 | 已完成 |
| 限额 + 比分网格 · 本地回放对齐 | 已完成 |
| 敞口值班台（四级限额 / 停盘 / 列表下钻） | 已完成 |
| Flink → decision.v1 / Redis 视图（代码） | 已完成 |
| 本机 Flink + Kafka 全链路联调 | 视部署环境 |
| 串关组合敞口 / 隐含概率加权 / sharp 分层 | 规划中（见方案文档） |

---

## License & compliance

Licensed under the **Apache License, Version 2.0** — see [LICENSE](LICENSE) and [NOTICE](NOTICE).

| Document | Purpose |
|----------|---------|
| [DISCLAIMER.md](DISCLAIMER.md) | Regulatory / AS-IS / demo credentials |
| [CONTRIBUTING.md](CONTRIBUTING.md) | DCO sign-off, PR expectations |
| [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | Community standards |
| [SECURITY.md](SECURITY.md) | Private vulnerability reporting |
| [docs/COMPLIANCE.md](docs/COMPLIANCE.md) | Release & license checklist |
| [docs/GOVERNANCE.md](docs/GOVERNANCE.md) | Lightweight Apache-style governance |

This project follows Apache-**style** open-source process; it is **not** (by default) an Apache Software Foundation project. See NOTICE.

Before publishing: rotate demo secrets, enable GitHub Security Advisories, and update `.github/ISSUE_TEMPLATE/config.yml` URLs.
