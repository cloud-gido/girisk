# 敞口看板产品逻辑

## 信息架构

```
KPI 总览（常驻）
  → 范围条：总体配置 | 球类 | 联赛 | 赛事工作台
  → 赛事工作台：筛选 + 全量赛事表 + 配置抽屉
```

顶栏四层均可**直接进入**；默认落在 **赛事工作台**（全量列表）。浏览器会记住上次选择（`localStorage`）。

| 层级 | 内容 |
|------|------|
| **总体配置** | KPI + 平台门控/限额 + Redis 高危场次 |
| **球类** | 球类切换 + 球类门控/限额 + 下属赛事 |
| **联赛** | 球类/联赛选择 + 联赛门控/限额 + 下属赛事 |
| **赛事工作台** | 筛选（ID/球类/联赛/状态/超额/门控）+ 全量表 + 行内配置抽屉 |

盘口**不是**独立导航层，挂在赛事配置抽屉内。

## 赛事唯一键与空壳同步

- **唯一识别**：`match_code` = Flink `fixtureId`
- Redis `girisk:view:fixture:*` 出现新 ID 时，Console **自动 upsert** `sports_match` 空壳（队名/联赛可留白）
- 运营在抽屉「赛事信息」补全：`PATCH /api/v1/sports/matches/{code}/meta`
- 列表：`GET /api/v1/sports/matches?sportCode&leagueCode&q&status&limitMode&gateOff`

## 四级限额 / 门控覆盖

优先级：**赛事 > 联赛 > 球类 > 总体 > 全局默认**（`girisk.sports.*`）。

| 层级 | API | Redis |
|------|-----|-------|
| 总体 | `GET\|PUT\|DELETE /api/v1/sports/scopes/overall/_/limit-override` | `girisk:override:scope:overall:_` |
| 球类 | `.../scopes/sport/{sportCode}/limit-override` | `girisk:override:scope:sport:{code}` |
| 联赛 | `.../scopes/league/{sport}/{league}/limit-override` | `girisk:override:scope:league:{sport}:{league}` |
| 赛事 | `.../matches/{code}/limit-override` | `girisk:override:fixture:{code}` |

门控三开关（总开关 / 限额 / 敞口）路径：`.../gates`（同层）。

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
                              └→ Console sync → sports_match 空壳 → 赛事工作台
```

## 赛事详情其它操作

- **停盘 / 开盘**：`POST /api/v1/sports/matches/{matchCode}/status`
- **元数据编辑**：`PATCH /api/v1/sports/matches/{code}/meta`
- **拦截结果汇总**：`girisk:view:fixture:{id}.replayStats`
- **各盘口明细**：同 key 的 `marketGroups`
- **非生产**：Console `ExposureStore` 仅供 HTTP decide / 演示，**不是**责任盘真相

## 演示数据

`exposure-demo` 空台自灌；也可 `POST /api/v1/sports/replay/demo`。内网回放脚本见 [DEMO-EXPOSURE.md](DEMO-EXPOSURE.md)（公开 GitHub 同步时排除）。
