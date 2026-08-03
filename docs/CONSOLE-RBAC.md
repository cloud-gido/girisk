# Console RBAC

用户 → 角色 → 权限码。JWT 携带 `roles` / `perms` / `operatorScope` / `jti`；API 用 `@PreAuthorize`；前端按权限裁剪菜单与路由。

## 权限码

| 权限码 | 含义 |
|--------|------|
| `monitor:read` | 总览 / 敞口看板 |
| `sandbox:use` | 调试沙箱（试算 / 管线 / 接口实验室） |
| `duty:write_match` | 联赛 / 赛事限额与闸门 |
| `duty:write_global` | 全局 / 运动级限额与闸门 |
| `case:review` | 审核工单 |
| `config:manage` | 配置发布 / 规则 / 策略 / 名单 |
| `audit:read` | 决策中心 / 风险回放 / 操作审计 |
| `iam:manage` | 账号与角色管理 |

## 内置角色

| 角色 | 权限 |
|------|------|
| ADMIN | 全部 |
| REVIEWER | monitor + sandbox + duty:write_match + case + audit |
| VIEWER | monitor + audit |
| TRADER | monitor + sandbox + duty:write_match |

## 默认账号

| 用户 | 密码 | 角色 |
|------|------|------|
| admin | admin123 | ADMIN |
| reviewer | review123 | REVIEWER |
| viewer | view123 | VIEWER |
| trader | trade123 | TRADER |

管理入口：登录 admin 后打开 **账号管理**（`/girisk/iam`）。

## 会话与吊销

- JWT 默认 **8 小时**（`JWT_EXPIRY_HOURS` / `girisk.jwt.expiry-hours`）。
- 每枚 token 带 `jti`；`POST /api/v1/auth/logout` 将 jti 加入黑名单。
- IAM **重置密码 / 停用账号** 会使该用户此前签发的 token 全部失效。
- 改权限后需 **重新登录** 才能拿到新 claims。

## 操作审计

运营写操作写入 `risk_event`（操作者在 `user_id`）：

| 前缀 | 含义 |
|------|------|
| `IAM_*` | 建用户 / 改资料 / 启停 / 重置密码 / 角色权限 |
| `DUTY_*` | 闸门 / 限额 / 赛事限额 / 停开盘 |
| `AUTH_*` | 登录 / 登出 |

前端：**操作审计**（`/girisk/ops-audit`，需 `audit:read`）。API：`GET /api/v1/ops-audit`。

## 商户范围（operatorScope）

- 用户字段 `operator_scope`：`*` = 不限；否则逗号分隔商户 ID 白名单。
- JWT claim `operatorScope`；IAM 可编辑。
- `girisk.tenant.enforce=true`（或 `GIRISK_TENANT_ENFORCE`）时，读决策/工单等按租户过滤，写操作校验范围。
- 值班写的操作者强制取自登录用户，不再信任请求体里的 `operatorId`（该字段历史含义是 updatedBy）。
