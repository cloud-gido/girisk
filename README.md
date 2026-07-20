# GiRisk（玑险）

**GiRisk**（代号 **GIRISK**，中文名 **玑险**）是 **gido（玑渡）** 产品族中的交易风控平台：运营台（Console）+ 契约 +（内网）实时决策 Engine。

- **公司 GitLab**：完整 monorepo（含 `girisk-engine`）
- **公开 GitHub**：只同步 Console + 契约——用 `scripts/sync-github.sh`，**不要**靠 `.gitignore` 藏 Engine（会对 GitLab 一并失效）

---

## 你能用它做什么

### GiRisk Console（玑险·台）

敞口看板、四级限额 / 停盘、决策审计与回放、REVIEW 工单、配置发布、试算沙箱。

### GiRisk Common（玑险·契）

`girisk.*` Topic、决策码、`config.v1` 消息，供 Console / Engine / 交易侧共用。

### GiRisk Engine（玑险·决）— 仅内网树

Flink 作业：等比例限额、比分矩阵敞口、写出 `girisk.decision.v1` 与 Redis 视图。打 jar 后由 **gido 实时作业**提交（`gido-flink-runtime`）。公开 GitHub 同步时排除本模块。

---

## 一条链路

```
交易 → Kafka 预检 → Engine（内网 / gido）
                      ↓
              girisk.decision.v1
                      ↓
        交易 · Console 审计 · Redis 大盘

Console 值班配置 → girisk.config.v1 → Engine
```

---

## 仓库结构（GitLab 全量）

```
girisk-common/     契约
girisk-console/    运营台
girisk-engine/     Flink 决策（不同步到公开 GitHub）
doris/             审计 DDL
docs/              说明
scripts/           演示 + sync-github.sh
deploy/            Console 镜像；flink/ 仅内网
```

| 文档 | 内容 |
|------|------|
| [docs/MONOREPO.md](docs/MONOREPO.md) | 模块与双远端 |
| [docs/PRODUCT-EXPOSURE.md](docs/PRODUCT-EXPOSURE.md) | 看板 / 四级限额 |
| [docs/AUDIT.md](docs/AUDIT.md) | Doris 审计 |
| [deploy/README.md](deploy/README.md) | Console → GHCR |

---

## 快速开始

### 环境

JDK 21+ · Maven 3.9+ · Node 18+ · Redis；（可选）Kafka / PostgreSQL 16 / Doris

### Console 演示

```bash
docker compose up -d redis
./start.sh --exposure-demo --background
# http://localhost:18088/girisk/exposure  admin / admin123
```

### 清洁链路 / 全栈

```bash
./start.sh --local --background
./start.sh --stack          # PG + Kafka + Redis + Doris + Console
```

### Engine jar（内网）

```bash
mvn -pl girisk-engine -am -DskipTests package
# → girisk-engine/target/girisk-engine-1.0.0.jar → gido 实时作业
```

### 镜像与对外同步

```bash
bash scripts/build-images.sh                 # 本地 Console 镜像
bash scripts/sync-github.sh                  # dry-run 导出公开子集
GITHUB_REMOTE=git@github.com:<org>/girisk.git bash scripts/sync-github.sh --push
```

公开 GHCR：`ghcr.io/<owner>/girisk/girisk-console:latest`（见 `.github/workflows/docker-publish.yml`）。

---

## License

Apache-2.0 — 见 [LICENSE](LICENSE)、[DISCLAIMER.md](DISCLAIMER.md)、[CONTRIBUTING.md](CONTRIBUTING.md)。
