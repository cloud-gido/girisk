# Console RBAC

用户 → 角色 → 权限码。JWT 携带 `roles` / `perms`；API 用 `@PreAuthorize`；前端按权限裁剪菜单与路由。

## 权限码

| 权限码 | 含义 |
|--------|------|
| `monitor:read` | 总览 / 敞口看板 |
| `sandbox:use` | 调试沙箱（试算 / 管线 / 接口实验室） |
| `duty:write_match` | 联赛 / 赛事限额与闸门 |
| `duty:write_global` | 全局 / 运动级限额与闸门 |
| `case:review` | 审核工单 |
| `config:manage` | 配置发布 / 规则 / 策略 / 名单 |
| `audit:read` | 决策中心 / 风险回放 |
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
