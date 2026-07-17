# GiRisk 品牌与技术命名

| 项 | 约定 |
|----|------|
| 产品名（展示） | **GiRisk** |
| 代号（全大写） | **GIRISK** |
| 产品族 | **gido** 全家桶 |
| 引擎 | GiRisk Engine（模块 `girisk-engine`） |
| 运营台 | GiRisk Console（模块 `girisk-console`） |
| 中文副标 | 风控决策引擎 |
| Maven groupId | `com.girisk` |
| Java 包根 | `com.girisk.*` |
| 配置前缀 | `girisk.*` |
| Topic 命名空间 | `girisk.*` |
| Redis 视图前缀 | `girisk:view:*` |
| UI 路由 | `/girisk/**` |
| 决策 API | `POST /api/v1/girisk/decide` |

## Topic 一览

| Topic | 用途 |
|-------|------|
| `girisk.trading.order.risk-check.v1` | 交易 → Engine 预检 |
| `girisk.trading.order.risk-check.post.v1` | 交易 → Engine 订单状态 |
| `girisk.decision.v1` | Engine → 交易 / Console（唯一决策出口） |
| `girisk.config.v1` | Console → Engine 配置（compact） |
| `girisk.sportsdata.fixture.match.summary` | 比分源 |
| `girisk.order.event` / `girisk.decision.event` | Console 调试流（非生产主路径） |

本地敞口回放演示（不依赖 Flink）：见 [DEMO-EXPOSURE.md](DEMO-EXPOSURE.md)。
