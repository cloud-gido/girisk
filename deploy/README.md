# GiRisk 镜像与部署

对齐 **giso / gido**：内部 GitLab 全量；公开 GitHub 仅 Console（`scripts/sync-github.sh`）。

## 职责拆分

| 组件 | 制品 | 运行 |
|------|------|------|
| **Console** | `girisk-console` → GHCR（公开 CI） | K8s / compose |
| **Engine** | shade jar（仅 GitLab 树内） | **gido 实时作业** + `gido-flink-runtime` |
| Kafka / Redis / PG / Doris | 基础设施 | compose / 平台 |

Engine **不要** `.gitignore`：内部需要版本管理；对外用 export 排除列表。

## Console 镜像（GitHub Actions）

公开仓同步后 CI 推送：

```text
ghcr.io/<owner>/girisk/girisk-console:latest
```

| 项 | 说明 |
|----|------|
| Dockerfile | [Dockerfile.console](Dockerfile.console) |
| CI | `.github/workflows/docker-publish.yml` |
| 本地 | `bash scripts/build-images.sh` |

## Compose

```bash
docker compose up -d
docker compose --profile full up -d --build
./start.sh --docker
```
