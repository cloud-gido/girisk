# GiRisk 仓库说明

本仓是 **完整 monorepo**（Console + 契约 + Engine），主要托管在**公司 GitLab**。

公开 **GitHub** 只同步 Console + 契约（见 [scripts/sync-github.sh](../scripts/sync-github.sh)），**不要**用 `.gitignore` 藏 Engine——ignore 会对 GitLab 一并生效，内部也丢跟踪。

品牌约定见 [BRANDING.md](BRANDING.md)。

## 模块

| 模块 | 产品角色 | GitLab | 公开 GitHub |
|------|----------|--------|-------------|
| `girisk-common` | Topic / 决策码 / config.v1 | ✅ | ✅ |
| `girisk-console` | 运营台 | ✅ | ✅ |
| `girisk-engine` | Flink 决策作业 | ✅ | ❌（export 排除） |

## 双远端模型

```text
开发 / CI（内部）     origin  → GitLab（全量，含 Engine）
对外开源同步          github  → GitHub（rsync 排除列表，见 scripts/github-export.exclude）
```

```bash
# 内部照常
git push origin main

# 对外（需已配 GITHUB_REMOTE 或 remote github）
bash scripts/sync-github.sh --push
```

## 架构

```
交易 → girisk.trading.order.risk-check.v1
         → GiRisk Engine（本仓 girisk-engine / gido 提交）
         → girisk.decision.v1 → 交易 / Console
         → Redis girisk:view:* → Console 敞口大盘

Console 配置发布 → girisk.config.v1 → Engine
```

## 构建

```bash
mvn -q verify

# Console
mvn -pl girisk-console -am -DskipTests package
./start.sh --local --background

# Engine jar → gido 实时作业
mvn -pl girisk-engine -am -DskipTests package
```
