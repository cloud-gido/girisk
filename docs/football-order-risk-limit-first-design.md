# 足球订单风控 v2 — 「先限额，后敞口」方案评估与实现设计

> 产品新方案：限额（等比例返彩平衡）恒开且前置，敞口（比分矩阵最差净盈亏）作为第二道硬闸门。
> 本文：① 梳理产品方案 ② 评估合理性/专业性（对照业界）③ 与现状差异 ④ 实现设计 ⑤ 待确认问题。
>
> 现行架构见 [football-order-risk-pipeline-design.md](football-order-risk-pipeline-design.md)（pre/post 双 topic 不变）。

---

## 1. 产品方案梳理

单笔新订单（pre PENDING）的判定流：

```text
payout_new = 投注金额 × 投注赔率        # 返彩金额（gross liability）

Gate 1 等比例限额（恒开）
  b_max = ((1+δ)·w·S_total − S_i) / (1 − (1+δ)·w)    # 组内互斥盘口，w=1/n，δ=0.2
  if payout_new >= b_max: 拒单，原因 = 限额不通过

Gate 2 风险敞口
  trial = Confirmed 集合 + 本笔
  worstPnl = min over 比分矩阵 (庄家净盈亏)
  if worstPnl < −阈值(示例 1000): 拒单，原因 = 风险敞口不通过

接受 → Confirmed 集合 + 盘口累计返彩更新（本架构中由 post.v1 CONFIRMED 驱动）
```

分组逻辑（与现有 `LimitMarketType` 一致）：

| 玩法 | 组 | 权重 |
|------|----|------|
| 胜平负 | 主胜/平/客胜 三向 | w=1/3 |
| 大小球 | 同一 line 的大/小 | w=1/2 |
| 让球 | ±同绝对值 line 的主/客 | w=1/2 |

公式数学上正确：b_max 是 `(S_i+b)/(S_total+b) ≤ (1+δ)·w` 的临界解，加满 b_max 后该盘口占比恰为上限（1X2 为 40%，两向为 60%）。产品文档中的三组算例（5000 / 6666.67 / 12000 等）均验算无误，且与现有 `ProportionalLimitCalculator` 行为一致（负值截断为 0）。

---

## 2. 合理性与专业性评估

### 2.1 符合业界实践的部分 ✅

| 点 | 评价 |
|----|------|
| **返彩（liability）口径** | 业界标准。庄家风控管的是 liability（潜在赔付），不是 handle（本金）。低赔率大额单与高赔率小额单的风险由返彩统一度量——比现行 stake 口径**明显更专业** |
| **等返彩 ≈ balanced book** | 组内返彩 1:1:…目标即「无论哪个结果发生，赔付大致相同」，是平衡账本的经典简化 |
| **闭式解 b_max** | O(1) 求解、可解释、可直接下发给交易端做前置校验，工程上优于迭代式优化 |
| **两道闸门分层** | 限额管单盘口结构失衡（gross），敞口管全场净风险（net，含收进来的本金对冲），互补自洽。限额便宜先算，顺序合理 |
| **互斥组定义** | 与主流交易系统的 market/line 分组一致 |

### 2.2 缺陷与风险 ⚠️

| # | 问题 | 严重度 | 说明 |
|---|------|--------|------|
| 1 | **冷启动死锁** | **已解决** | `S_total=0 → b_max=0`，而规则是 `payout_new >= b_max → 拒`，即每个新盘口组的**第一笔单必被拒**。产品计算器已给出解法：**「初始已投注金额」虚拟种子**（默认 2000，返彩口径）——分组首次出现时组内每个盘口先记入种子再算公式。种子进入 S_i/S_total，失衡后 b_max 自然归零，且随真实量增大自动稀释，优于 floor 方案（floor 会让热门方向每笔仍放行 floor 额度）。首单容量闭式解：胜平负 = 种子/3，两向盘 = 种子/2 |
| 2 | **等权忽略赔率结构** | 中 | 强弱悬殊场次热门方向天然吸量，等权会系统性限死热门、给冷门开大额度。业界进阶做法按隐含概率加权 `w_i ∝ 1/odds_i`（归一化），让**净赔付**而非 gross 返彩均衡。建议权重策略可配置，本期默认等权（与产品案一致） |
| 3 | **敞口阈值语义突变** | 中 | 现行 `exposureThresholdYuan=12000` 是「超过才启用限额」的软开关；新方案变成「最差净亏 > 阈值即硬拒」。-1000 显然是示例值，生产值需产品拍板，且参数必须**改名**防止旧配置误用 |
| 4 | **让球半盘返彩为上界近似** | 低 | quarter-line 半赢半输时实际赔付 < stake×odds，用全额做限额偏保守，可接受，注明即可 |
| 5 | **边界符号** | 低 | 产品用 `>=`（等于也拒）；现行代码 `> acceptMax` 才拒。按产品统一为 `>=` |
| 6 | **未覆盖玩法/串关** | 低 | 正确比分、串关等不在三类分组内——维持现状（仅解析不限额），走 Gate 2 敞口兜底 |

### 2.3 业界参考对照

成熟做法通常是四层：**单笔上限（per-bet max stake/payout）→ 盘口 liability 平衡（本方案 Gate 1）→ 赛事级最差敞口上限（本方案 Gate 2）→ 人工 trader 干预/对冲（lay-off）**。产品方案覆盖了中间两层，结构上站得住；缺第一层「单笔绝对上限」与 floor（正好和冷启动修复合并解决），第四层由业务下游 override post.v1 天然承担。

**结论：方案方向专业、公式正确、可落地；必须先补冷启动 floor、定敞口生产阈值，权重策略留扩展点。**

---

## 3. 与现状差异

| 项 | 现状 | v2 新方案 |
|----|------|-----------|
| 限额启用 | `limitMode` = 试探敞口 ≥ 12000 才启用 | **恒开**，作为第一道闸门 |
| 限额口径 | 本金 `stakeYuan` | **返彩 = stake × odds** |
| 敞口角色 | 决定 limitMode 的软开关 | **独立硬闸门**：trial 最差净亏 > 阈值 → 拒 |
| 拒单原因 | 单一 `shouldReject` | `rejectReason`: `LIMIT` / `EXPOSURE` / null |
| 边界 | `payout > acceptMax` 拒 | `payout >= acceptMax` 拒 |
| b_max 公式 / 分组 / δ | `ProportionalLimitCalculator` / `LimitMarketType` / 0.2 | **不变，直接复用** |
| 最差净盈亏 | `MatchExposureAggregator.maxBookmakerLossCents` 已有 | 复用，仅接入判定 |
| pre/post 架构 | pre 建议 + post 权威 state | **不变**（产品的「更新 Confirmed 集合」= post CONFIRMED 入窗） |

产品伪代码里的「更新该盘口累计返彩金额」**不需要单独维护累计器**：每次 pre 从 post CONFIRMED 窗口重算聚合（现行模式），天然吸收 REJECTED / CASHED_OUT 出窗，无状态漂移风险。

---

## 4. 实现设计

### 4.1 决策流（`MatchTriggerAcceptance` v2）

```text
输入：confirmed（post CONFIRMED 窗口）、trigger（本笔 PENDING）、grid、δ、
      initialSeedPayoutYuan、maxWorstLossYuan

1. payoutNew = trigger.stakeCents × odds          # 分，BigDecimal
2. Gate 1：按返彩聚合 confirmed，组内每个盘口 += 虚拟种子 seed（冷启动，
   与产品计算器 ensureGroup 一致；聚合时叠加即可，无需持久化 state）
   → 组内 b_max（复用 ProportionalLimitCalculator）
   if payoutNew >= b_max → reject(LIMIT)
3. Gate 2：trial = confirmed + trigger
   worstLoss = MatchExposureAggregator.summarize(trial, grid).maxBookmakerLossCents
   if worstLoss > maxWorstLossYuan × 100 → reject(EXPOSURE)
4. accept（建议值；业务经 post.v1 定终态）
```

### 4.2 模块改动

| 模块 | 改动 |
|------|------|
| `MarketStakeAggregator` | 聚合值 `stakeYuan` → **payout（stake × odds，分精度 BigDecimal）**；类语义改为 liability 聚合 |
| `MatchTriggerAcceptance` | 重排为两道闸门；产出 `rejectReason`；`limitMode` 概念退役（恒 true 或删除） |
| `LimitRejectionPolicy` | 比较值改 payout、`>=` 边界、floor 逻辑 |
| `ExposureLimitGate` | `shouldApplyLimit`（软开关）退役；保留 `maxExposureYuan` 供 Gate 2 与输出 |
| `MatchLimitSummaryJson` | schemaVersion 4：`basis:"payout"`、金额字段为返彩口径、`rejectReason`、`minAcceptPayoutYuan` |
| `MatchExposureSummaryJson` | 增 `worstNetPnlYuan`（= −maxBookmakerLoss，直接对齐产品术语） |
| business topic | 结构不变（union all），透传新字段 |

### 4.3 新参数

```bash
--limit.delta                 0.2      # 不变
--limit.basis                 payout   # 标识口径（本期固定 payout）
--limit.initialSeedPayoutYuan 2000     # 每盘口虚拟种子（产品计算器默认 2000，返彩口径）
--exposure.maxWorstLossYuan   <待定>   # Gate 2 硬阈值（产品示例 1000）
# 退役：--limit.exposureThresholdYuan（软开关语义，防误配不复用旧名）
```

### 4.4 分阶段落地

| 阶段 | 内容 | 验收 | 状态 |
|------|------|------|------|
| P1 核心 | payout 聚合 + 两道闸门 + rejectReason + 冷启动种子 | 单测：产品三组算例逐数字断言（5000/6666.67/12000/0）；种子=2000 时首单容量 666.67/1000 | **已完成** |
| P2 输出 | limit v4 / summary 字段 / business 透传 / 文档 | schema 测试 + 文档同步 | **已完成** |
| P3 模拟 | 10 条模拟扩展：LIMIT 拒（事件 5：1104 >= 459.67）、种子放行、CASHED_OUT 释放额度 | `PostFeedbackTenEventSimulationTest` 全绿 | **已完成** |
| P4 上线 | K8s 参数（seed=2000 / worstLoss=1000 已写入 prod yaml）、与交易侧联调 | prod 联调通过 | 待联调 |

---

## 5. 待产品确认

> 产品提供的 HTML 计算器（限额公式_投注额乘赔率_风险敞口拦截计算器.html）已确认：冷启动用「初始已投注金额」虚拟种子（默认 2000）；边界 `>=` 拒单；限额只在接收后累加；敞口用临时接收后最差盈亏判定；阈值 1000/-1000 等价。

1. **种子生产值**——计算器默认 2000，是否即生产值？
2. **敞口生产阈值**——计算器默认 1000（即最差净亏 < -1000 拒），是否即生产值？
3. 权重本期等权确认；隐含概率加权是否列入后续
4. 让球半盘：敞口矩阵按半赢半输精算（计算器已实现），限额占用按 stake×odds 全额，确认此口径
5. **让球分组方向**：计算器分组 key 含方向（主-1/客+1 与 主+1/客-1 是不同组），需与 `LimitMarketType.HANDICAP` 分组键核对对齐
6. 三类玩法之外的订单只走 Gate 2 敞口、不做组限额，确认
