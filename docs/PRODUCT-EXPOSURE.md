# 敞口看板产品逻辑

## 信息架构

顶栏四层均可**直接进入**（不必先选父层）：

```
总体 | 球类 | 联赛 | 赛事
```

每层默认先出**列表**，再点进**详情**。浏览器会记住上次选择（`localStorage`）。

| 层级 | 列表 | 详情 |
|------|------|------|
| **总体** | KPI + 球类速览 + 高危表 | 总体限额配置 |
| **球类** | 全部球类 | 球类限额 + 下属联赛 |
| **联赛** | 全部联赛 | 联赛限额 + 下属赛事 |
| **赛事** | 全部赛事 | 赛事限额 + 拦截汇总 + 盘口明细 |

盘口**不是**独立导航层，挂在赛事详情下。

## 四级限额覆盖

优先级：**赛事 > 联赛 > 球类 > 总体 > 全局默认**（`girisk.sports.*`）。

| 层级 | API | Redis |
|------|-----|-------|
| 总体 | `GET\|PUT\|DELETE /api/v1/sports/scopes/overall/_/limit-override` | `girisk:override:scope:overall:_` |
| 球类 | `.../scopes/sport/{sportCode}/limit-override` | `girisk:override:scope:sport:{code}` |
| 联赛 | `.../scopes/league/{sport}/{league}/limit-override` | `girisk:override:scope:league:{sport}:{league}` |
| 赛事 | `.../matches/{code}/limit-override`（兼容旧路径） | `girisk:override:fixture:{code}` |

字段：δ / 冷启动种子 / 最差亏损阈值 / 单注返彩上限。本层未覆盖的字段继承上层。

## 配置下发 Flink（`girisk.config.v1`）

值班改门控/限额后，Console 除写 Redis 外，会发布消息到 **`girisk.config.v1`**（`kind=SCOPE_OVERRIDE`，Kafka key=`MATCH:…` / `LEAGUE:sport:code` 等）。

**生产级保障：**
- Topic `cleanup.policy=compact`（创建时写入；已存在则 alter）
- **Transactional outbox**：覆盖写与 `girisk:outbox:scope-config` 入队同一 Redis `MULTI/EXEC`；Poller 异步投递（latest-wins）
- Producer：`acks=all` + 幂等 + 同步 `get` + 应用层重试（默认 5 次）
- 失败：requeue；超限进 `girisk:outbox:scope-config:dlq` 并写 `CONFIG_OUTBOX_DLQ` / `CONFIG_PUBLISH_FAIL`；HTTP 写路径不因 Kafka 短暂故障失败
- 启动：自动全量 Redis→Kafka 重刷；运维接口 `POST /api/v1/sports/scopes/config-sync`（ADMIN）
- 关闭 outbox（回退同步发）：`KAFKA_CONFIG_OUTBOX_ENABLED=false`

外部决策引擎（内部仓）消费 `girisk.config.v1`：解析优先级单赛事 > 联赛 > 球类 > 总体 > CLI 默认。

**全流程（订单 → 页面）：**
```text
risk-check.v1 → 决策引擎（内部）→ decision.v1 → Console 决策页
                              ├→ Redis girisk:view:fixture.replayStats → 拦截结果汇总
                              └→ Redis girisk:view:fixture.marketGroups → 各盘口明细
```

## 赛事详情其它操作

- **停盘 / 开盘**：`POST /api/v1/sports/matches/{code}/status`
- **拦截结果汇总**：`girisk:view:fixture:{id}.replayStats`（引擎 decision 累加）。口径：`有效 = 接收 + 拦截 + 重复`（`duplicateCount` 来自 `evidence.duplicateIgnored`）
- **各盘口明细**：同 key 的 `marketGroups`（引擎 Gate1 限额快照；与汇总同源）
- **非生产**：Console `ExposureStore`（`sports:stake:*` / `sports:payout:*`）仅供 HTTP decide / 演示，**不是**责任盘真相

## 演示数据

`exposure-demo` 空台自灌；也可 `POST /api/v1/sports/replay/demo`。内网回放脚本见 [DEMO-EXPOSURE.md](DEMO-EXPOSURE.md)（公开 GitHub 同步时排除）。
