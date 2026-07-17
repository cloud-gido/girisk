# 生产环境：团队协作部署指南

内网域名统一使用 **`yfls.internal`**（可在 DNS 或 `/etc/hosts` 配置）。

## 一、架构总览

```
开发者 / GitLab CI
       │
       ├─ git tag ──► build JAR + docker push ──► harbor.yfls.internal/flink/football-order-kafka
       │
       └─ kubectl ──► k8s-api.yfls.internal:6443
                           │
                           ├─ namespace: flink   → Flink Operator 1.15 + FootballOrderKafkaJob
                           ├─ namespace: harbor  → 私有镜像仓库
                           ├─ namespace: minio   → checkpoint / savepoint
                           └─ ingress            → headlamp / flink-ui / harbor UI
```

## 二、已定配置（可直接用）

| 项 | 取值 |
|----|------|
| K8s 版本 | 1.33.7 |
| 节点规模 | 3 worker（每节点 8C16G 起） |
| API 地址 | `https://k8s-api.yfls.internal:6443` |
| Harbor | `https://harbor.yfls.internal` |
| MinIO | `https://minio.yfls.internal`，bucket `flink-checkpoints` |
| Kafka | `kafka.yfls.internal:9092` |
| Headlamp | `https://headlamp.yfls.internal` |
| Flink UI | `https://girisk-engine.yfls.internal` |
| CI | GitLab CI |
| 认证 | 一期 RBAC + kubeconfig；二期 OIDC（Keycloak） |
| Flink | 2.0.1，`flinkVersion: v2_0` |
| Operator | 1.15.0 |

## 三、内网 DNS（/etc/hosts 示例）

```
10.10.0.10   k8s-api.yfls.internal
10.10.0.11   harbor.yfls.internal
10.10.0.12   minio.yfls.internal
10.10.0.13   headlamp.yfls.internal
10.10.0.14   girisk-engine.yfls.internal
10.10.0.20   kafka.yfls.internal
```

生产用内网 DNS 服务器替换 hosts。

## 四、安装顺序

### 1. 基础组件

```bash
# cert-manager
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.18.2/cert-manager.yaml

# ingress-nginx（内网入口）
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm install ingress-nginx ingress-nginx/ingress-nginx \
  -n ingress-nginx --create-namespace \
  --set controller.service.type=LoadBalancer
```

### 2. Harbor

```bash
helm repo add harbor https://helm.goharbor.io
helm install harbor harbor/harbor -n harbor --create-namespace -f harbor-values.yaml
```

创建项目 `flink`，为 CI 创建 Robot Account，记下 token。

### 3. MinIO（checkpoint）

```bash
kubectl apply -f minio.yaml
# 创建 bucket: flink-checkpoints
# 记下 access-key / secret-key，填入 flink-deployment.yaml 的 secret
```

### 4. Flink Operator 1.15

```bash
helm repo add flink-operator-repo https://downloads.apache.org/flink/flink-kubernetes-operator-1.15.0/
helm install flink-kubernetes-operator flink-operator-repo/flink-kubernetes-operator \
  -n flink --create-namespace \
  -f flink-operator-values.yaml
```

### 5. RBAC + 团队成员 kubeconfig

```bash
kubectl apply -f rbac/
# 平台管理员为成员签发 kubeconfig（见 rbac/README.md）
```

### 6. Harbor 拉镜像 Secret

```bash
kubectl apply -f harbor-pull-secret.yaml
# 编辑 harbor-pull-secret.yaml 填入 robot token
```

### 7. 部署作业

```bash
# 先 CI 构建并 push 镜像，再：
kubectl apply -f flink-deployment.yaml
```

### 8. Headlamp（可选）

```bash
kubectl apply -f headlamp.yaml
```

## 五、团队日常流程

1. 开发在 OrbStack 本地调试
2. 合并 main → 打 tag `v1.0.x`
3. GitLab CI 自动：mvn package → docker build → push Harbor → kubectl apply
4. Flink Operator 滚动升级 / savepoint
5. Headlamp / Flink UI 查看状态

## 六、文件说明

| 文件 | 用途 |
|------|------|
| `harbor-values.yaml` | Harbor Helm 配置 |
| `minio.yaml` | MinIO + Ingress |
| `flink-operator-values.yaml` | Operator 1.15 |
| `harbor-pull-secret.yaml` | 拉镜像凭证模板 |
| `flink-deployment.yaml` | FootballOrderKafkaJob 生产部署 |
| `Dockerfile` | 作业镜像 |
| `.gitlab-ci.yml` | CI/CD 流水线 |
| `rbac/` | 团队权限 |
| `headlamp.yaml` | 内网集群管理页 |
