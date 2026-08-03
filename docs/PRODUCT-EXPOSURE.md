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

## 限额 / 门控覆盖（分层）

优先级：

```text
MATCH_PRE | MATCH_LIVE   （有则用；按订单 gameState/isLive：赛前 / 滚球）
    ↓
MATCH                    （整场覆盖）
    ↓
LEAGUE → SPORT → OVERALL → 全局默认（girisk.sports.* / Engine CLI）
```

未配赛前/滚球时，与整场同源（都落到同一 properties 默认或上层覆盖）。

| 层级 | API | Redis |
|------|-----|-------|
| 总体 | `GET\|PUT\|DELETE /api/v1/sports/scopes/overall/_/limit-override` | `girisk:override:scope:overall:_` |
| 球类 | `.../scopes/sport/{sportCode}/limit-override` | `girisk:override:scope:sport:{code}` |
| 联赛 | `.../scopes/league/{sport}/{league}/limit-override` | `girisk:override:scope:league:{sport}:{league}` |
| 赛事整场 | `.../matches/{code}/limit-override?segment=all`（默认） | `girisk:override:fixture:{code}` |
| 赛事赛前 | `.../matches/{code}/limit-override?segment=pre` | `girisk:override:scope:match_pre:{code}` |
| 赛事滚球 | `.../matches/{code}/limit-override?segment=live` | `girisk:override:scope:match_live:{code}` |

门控三开关（总开关 / 限额 / 敞口）路径：`.../gates`（同层；赛前/滚球目前只覆盖限额参数）。

字段：δ / 冷启动种子 / 最差亏损阈值（风险阈值；看板「盈亏线」为其负号写法，非独立配置） / 单注返彩上限。本层未覆盖的字段继承上层。

**系统默认（Console `girisk.sports.*` ↔ Engine `limit.seedPayoutYuan` / `exposure.maxWorstLossYuan`）**：冷启动种子 **5000** 元、风险阈值 **200000** 元。与指标卡「初始已投注金额/盘口」「风险阈值」对齐。

**未改 Console 时，整场 / 赛前 / 滚球 Tab 的生效阈值必须一致**（都落到总体→…→上述默认；Engine 同理落到 CLI）。只有在赛事上显式写了 `segment=pre|live` 覆盖后才会分叉。`sports_match.exposure_threshold` 仅为列表缓存，不参与静默默认。

Kafka key：`MATCH_PRE:{fixtureId}` / `MATCH_LIVE:{fixtureId}`（与 `MATCH:{id}` 同构），经 outbox 下发 `girisk.config.v1`。

## 配置下发 Flink（`girisk.config.v1`）

值班改门控/限额后，Console 除写 Redis 外，会发布消息到 **`girisk.config.v1`**（`kind=SCOPE_OVERRIDE`，Kafka key=`MATCH:…` / `MATCH_PRE:…` / `MATCH_LIVE:…` / `LEAGUE:sport:code` 等）。

**生产级保障：**
- Topic `cleanup.policy=compact`（创建时写入；已存在则 alter）
- **Transactional outbox**：覆盖写与 `girisk:outbox:scope-config` 入队同一 Redis `MULTI/EXEC`；Poller 异步投递（latest-wins）
- Producer：`acks=all` + 幂等 + 同步 `get` + 应用层重试（默认 5 次）
- 失败：requeue；超限进 `girisk:outbox:scope-config:dlq` 并写 `CONFIG_OUTBOX_DLQ` / `CONFIG_PUBLISH_FAIL`；HTTP 写路径不因 Kafka 短暂故障失败
- 启动：自动全量 Redis→Kafka 重刷；运维接口 `POST /api/v1/sports/scopes/config-sync`（ADMIN）
- 关闭 outbox（回退同步发）：`KAFKA_CONFIG_OUTBOX_ENABLED=false`

外部决策引擎（内部仓）消费 `girisk.config.v1`：解析优先级 **赛前/滚球 > 单赛事 > 联赛 > 球类 > 总体 > CLI 默认**（按订单 `isLive`/`gameState` 选 MATCH_PRE 或 MATCH_LIVE）。引擎实现细节仅在公司 GitLab（`girisk-engine`），不公开同步。

**全流程（订单 → 页面）：**
```text
risk-check.v1 → 决策引擎（内部）→ decision.v1 → Console 决策页
                              ├→ Redis girisk:view:fixture.replayStats（决策口径：接收/拦截/重复）
                              └→ Redis girisk:view:fixture.marketGroups → 各盘口明细（敞口数值）
                              └→ Console sync → sports_match 空壳 → 赛事工作台
```

**决策 vs 敞口口径（易混）：**

| 指标 | 来源 | 含义 |
|------|------|------|
| 决策中心条数 | Console PG：同 `traceId`/`requestId` **幂等只留 1 行** | 运营台账 |
| 拦截汇总「接收·拦截·重复」 | Redis：按 **orderId** 幂等累加（`girisk:idem:fixture-decision:*`）；Flink `preSeen` 标 duplicate | post 不参与重复；重放不虚高 |
| 盘口「已投注」、最差盈亏等敞口值 | 确认池（post CONFIRMED；无 post 时=已 PASS） | post 只刷新风险敞口数值 |
| Doris 行数 | Kafka 原样追加（可同单多行） | 审计面；查询按 `trace_id` 取最新 |

## 赛前 / 滚球下钻（类 GROUPING SETS）

仍 `keyBy(fixtureId)`；Gate 决策用 **ALL**。PRE/LIVE 与 ALL **同一 Redis 契约**，只是订单集按 `gameState` 过滤。

| 维度 | Redis | Tab |
|------|-------|-----|
| ALL | 顶层 `replayStats` / `marketGroups` / `confirmedOrders` | 整个赛事 |
| PRE / LIVE | `segments.pre` / `segments.live` 同构字段 | 赛前 / 滚球 |

### 统一契约（`FixtureViewOrderSets`）

```text
heldRisk  = 拒单 → confirmed；接单 → trialIncludingTrigger（无 post = 已 PASS）
confirmed = post CONFIRMED 真确认池（不含仅 PASS 未确认）

decision.v1 ──► acceptedCount / rejected* / duplicate* / acceptedStakeYuan(本金)
                 ALL + 对应 segment（按 isLive）
summary/limit ─► marketGroups、withRiskWorst*  ← heldRisk
                 confirmedPoolCount/Stake、hash.confirmedOrders ← confirmed
post / 比分   ──► 只刷新敞口块，永不改写决策计数
```

| 字段 | 含义 | 单位 |
|------|------|------|
| `replayStats.acceptedCount` | 决策 PASS（orderId 幂等） | 单 |
| `replayStats.acceptedStakeYuan` | 决策 PASS 累计 | **本金** |
| `marketGroups[].outcomes.actualStake` | 持险已投注 | **返彩** |
| `confirmedPoolCount` / hash `confirmedOrders` | 真确认池 | 单 |
| `withRiskWorstPnlYuan` | 持险最差庄家盈亏 | 元 |

**禁止：** 赛前/滚球 Tab 回退 ALL 的 `worst*`；把 `acceptedCount` 写进 `confirmedOrders`。

订单标记：以首腿 `gameState` 为准（`PreMatch`→赛前，`InPlay`→滚球）；缺省才回退 `payload.isLive` / `inPlay`。`payload.phase=PRE_CONFIRM` 是确认生命周期，不是赛段。再缺省视为赛前。

```text
订单(gameState) ─► Book ALL（Gate0/1/2）
                 ├► Book PRE  ─┐
                 └► Book LIVE ─┴► Redis segments → Tab 下钻
```

## 事实表 × 维度表（比分降载）

业界风控常见不对称连接：**订单是事实表（小）**，**赛事/比分是维度表（大）**。GiRisk Engine 约定：

| 流 | 角色 | 行为 |
|----|------|------|
| **pre** | 事实（PENDING） | 原始试探订单流，topic 上**不会拒单**；引擎限额+矩阵后写 PASS/REJECT 到 `girisk.decision.v1` |
| **post** | 事实反馈 | 交易确认态：CONFIRMED 入限额池 / REJECTED·兑现出池；刷新敞口视图，**不改**历史决策 |
| **live-score** | 维度 | 全量 feed；仅对「出现过订单」的 `fixtureId` 处理 |

降载链路（默认 `--live.score.active.fixture.filter true`）：

```text
全量比分 Kafka
  → peek fixtureId（轻量扫字段，不全量构 LiveMatchScore）
  → 兴趣集过滤（订单侧 broadcast 活跃 fixture）
  → 全量 parse
  → keyBy → CoProcess
  → 有效比分/相位未变则跳过；变分才重算敞口 / Redis
```

首单到达前该场比分会被滤掉；入单后等下一跳比分对齐网格（与「持仓订阅行情」一致）。

## 赛事详情其它操作

- **停盘 / 开盘**：`POST /api/v1/sports/matches/{matchCode}/status`
- **元数据编辑**：`PATCH /api/v1/sports/matches/{code}/meta`
- **拦截结果汇总**：`girisk:view:fixture:{id}.replayStats`
- **各盘口明细**：同 key 的 `marketGroups`
- **非生产**：Console `ExposureStore` 仅供 HTTP decide / 演示，**不是**责任盘真相

## 演示数据

`exposure-demo` 空台自灌；也可 `POST /api/v1/sports/replay/demo`。内网回放脚本见 [DEMO-EXPOSURE.md](DEMO-EXPOSURE.md)（公开 GitHub 同步时排除）。
