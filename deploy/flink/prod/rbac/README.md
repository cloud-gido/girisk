# 团队 RBAC 与 kubeconfig

## 角色

| 用户/账号 | 权限 | 用途 |
|-----------|------|------|
| `platform-admin` | cluster-admin | 平台管理员 |
| `flink-team` (Group) | flink namespace 开发 | 日常开发部署 |
| `ci-deploy` (SA) | flink namespace 部署 | GitLab CI |

## 应用 RBAC

```bash
kubectl apply -f flink-developer-role.yaml
kubectl apply -f ci-deploy-sa.yaml
```

## 为团队成员签发 kubeconfig

平台管理员在生产 master 上执行（以 `zhangsan` 为例）：

```bash
# 1. 创建客户端证书（或用企业 CA / cfssl）
# 简化做法：复制管理员 kubeconfig，改为受限 ServiceAccount token

# 2. 为 flink-team 成员创建 SA token（开发环境简化方案）
kubectl create serviceaccount zhangsan -n flink
kubectl create rolebinding zhangsan-flink-dev \
  --role=flink-developer -n flink --serviceaccount=flink:zhangsan

kubectl create token zhangsan -n flink --duration=8760h > zhangsan.token

# 3. 组装 kubeconfig（成员保存为 ~/.kube/yfls-prod.yaml）
```

成员 kubeconfig 模板：

```yaml
apiVersion: v1
kind: Config
clusters:
- cluster:
    certificate-authority-data: <CA_BASE64>
    server: https://k8s-api.yfls.internal:6443
  name: yfls-prod
contexts:
- context:
    cluster: yfls-prod
    namespace: flink
    user: zhangsan
  name: yfls-prod
current-context: yfls-prod
users:
- name: zhangsan
  user:
    token: <TOKEN>
```

## 二期 OIDC（Keycloak）

- Issuer: `https://sso.yfls.internal/realms/yfls`
- K8s API 配置 `--oidc-*` 参数
- Headlamp 配置 OIDC 登录，取消长期 cluster-admin token
