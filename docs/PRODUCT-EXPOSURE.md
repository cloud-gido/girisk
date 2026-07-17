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

## 赛事详情其它操作

- **停盘 / 开盘**：`POST /api/v1/sports/matches/{code}/status`
- **拦截结果汇总**：`girisk:view:fixture:{id}.replayStats`
- **各盘口明细**：等比例 δ 下各 selection 已投 / 上限 / 还能接

## 演示数据

`exposure-demo` 空台自灌；也可 `POST /api/v1/sports/replay/demo`。见 [DEMO-EXPOSURE.md](DEMO-EXPOSURE.md)。
