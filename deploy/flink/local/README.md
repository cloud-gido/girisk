# 本地 K8s + Flink Operator（局域网协作极简版）

目标：Mac 本机跑 K8s，局域网同事用 `kubectl` 部署 Flink 作业。

## 你已经有的

- OrbStack `ubuntu` 虚拟机里的 K8s（k3s）
- Flink Operator 1.15（`flink` namespace）
- Headlamp：`http://<Mac局域网IP>:8050`

## 还缺什么

局域网机器要能连 **K8s API（6443）**，并拿到 **kubeconfig**。

---

## 第一步：确认本机 K8s 正常

在 **ubuntu** 里：

```bash
kubectl get nodes
kubectl get pods -n flink
kubectl get crd | grep flink
```

---

## 第二步：把 API 暴露给局域网（Mac 转发 6443）

和 Headlamp 一样，在 **Mac** 上用 Python 常驻转发 API。

### 1. 写脚本 `~/bin/k8s-api-proxy.py`

```python
#!/usr/bin/env python3
import socket, threading

LISTEN_PORT = 6443
TARGET_HOST = "192.168.139.119"   # ubuntu IP，hostname -I 第一个
TARGET_PORT = 6443

def pipe(a, b):
    try:
        while True:
            d = a.recv(65536)
            if not d: break
            b.sendall(d)
    except Exception:
        pass
    finally:
        a.close(); b.close()

def handle(c):
    try:
        r = socket.create_connection((TARGET_HOST, TARGET_PORT), timeout=10)
    except Exception:
        c.close(); return
    threading.Thread(target=pipe, args=(c, r), daemon=True).start()
    threading.Thread(target=pipe, args=(r, c), daemon=True).start()

s = socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(("0.0.0.0", LISTEN_PORT)); s.listen(128)
while True:
    c, _ = s.accept()
    threading.Thread(target=handle, args=(c,), daemon=True).start()
```

### 2. Mac launchd 自启

```bash
chmod +x ~/bin/k8s-api-proxy.py

sudo tee /Library/LaunchDaemons/com.infras.k8s-api-proxy.plist >/dev/null <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>com.infras.k8s-api-proxy</string>
  <key>ProgramArguments</key>
  <array>
    <string>/usr/bin/python3</string>
    <string>/Users/infras/bin/k8s-api-proxy.py</string>
  </array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
</dict>
</plist>
EOF

sudo launchctl bootstrap system /Library/LaunchDaemons/com.infras.k8s-api-proxy.plist
```

### 3. Mac 上验证

```bash
curl -k https://127.0.0.1:6443/version
```

---

## 第三步：导出 kubeconfig 给同事

在 **ubuntu** 里：

```bash
kubectl config view --raw > /tmp/k8s-local.yaml
cat /tmp/k8s-local.yaml
```

把文件发给同事，让他们改 `clusters[0].cluster.server` 为：

```text
https://<Mac局域网IP>:6443
```

开发环境证书不匹配时，在该 cluster 下加：

```yaml
insecure-skip-tls-verify: true
```

同事测试：

```bash
export KUBECONFIG=./k8s-local.yaml
kubectl get nodes
kubectl get pods -n flink
```

---

## 第四步：同事部署 Flink 作业

### 方式 A：kubectl apply YAML

```bash
kubectl apply -f flink-deployment.yaml
kubectl get flinkdeployment -n flink
```

### 方式 B：Headlamp 网页

浏览器打开 `http://<Mac局域网IP>:8050`，粘贴 YAML 或看 Pod。

### 镜像说明（本地最简单）

OrbStack 本机构建的镜像，K8s 通常能直接用。同事在 **ubuntu** 里 build：

```bash
# 在 ubuntu 或 Mac 通过 orb 执行
mvn clean package -DskipTests
docker build -f k8s/local/Dockerfile -t football-order-kafka:dev .
```

`FlinkDeployment` 里 `image` 填 `football-order-kafka:dev`，`imagePullPolicy: IfNotPresent`。

---

## 可选：限制同事权限（别给 cluster-admin）

```bash
kubectl apply -f - <<'EOF'
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: flink-dev
  namespace: flink
rules:
  - apiGroups: ["", "flink.apache.org"]
    resources: ["pods", "pods/log", "services", "flinkdeployments", "flinksessionjobs"]
    verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: flink-dev-binding
  namespace: flink
subjects:
  - kind: ServiceAccount
    name: dev-user
    namespace: flink
roleRef:
  kind: Role
  name: flink-dev
  apiGroup: rbac.authorization.k8s.io
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: dev-user
  namespace: flink
EOF

kubectl -n flink create token dev-user --duration=8760h
```

把 token 写入同事 kubeconfig 的 `users[].user.token`（不要用管理员 kubeconfig）。

---

## 地址汇总

| 用途 | 地址 |
|------|------|
| kubectl API | `https://<Mac局域网IP>:6443` |
| Headlamp | `http://<Mac局域网IP>:8050` |
| Flink UI | `kubectl port-forward` 或 Ingress（按需） |

---

## 不需要的（先别装）

- Harbor
- GitLab CI
- MinIO
- 内网 DNS
- cert-manager（本地开发）

`k8s/prod/` 目录留给以后上生产再用。
