export type SessionUser = {
  username: string;
  role: string;
  displayName: string;
  roles?: string[];
  permissions?: string[];
  operatorScope?: string;
};

export function getToken(): string | null {
  return localStorage.getItem('risk_token');
}

export function setAuth(
  token: string,
  username: string,
  role: string,
  displayName: string,
  roles: string[] = [],
  permissions: string[] = [],
  operatorScope: string = '*',
) {
  localStorage.setItem('risk_token', token);
  localStorage.setItem(
    'risk_user',
    JSON.stringify({
      username,
      role,
      displayName,
      roles,
      permissions,
      operatorScope,
    } satisfies SessionUser),
  );
}

export function clearAuth() {
  localStorage.removeItem('risk_token');
  localStorage.removeItem('risk_user');
}

export function getUser(): SessionUser | null {
  const raw = localStorage.getItem('risk_user');
  return raw ? (JSON.parse(raw) as SessionUser) : null;
}

export function isLoggedIn(): boolean {
  return !!getToken();
}

export function getPermissions(): string[] {
  return getUser()?.permissions ?? [];
}

export function hasPerm(perm: string): boolean {
  return getPermissions().includes(perm);
}

export function hasAnyPerm(...perms: string[]): boolean {
  const mine = getPermissions();
  return perms.some((p) => mine.includes(p));
}

/** 系统管理员（ROLE_ADMIN），用于破坏性运维入口。 */
export function isAdmin(): boolean {
  const u = getUser();
  if (!u) return false;
  if (u.role === 'ADMIN') return true;
  return (u.roles ?? []).includes('ADMIN');
}

/** 权限码常量（与后端 RbacPermissions 对齐） */
export const Perm = {
  MONITOR_READ: 'monitor:read',
  SANDBOX_USE: 'sandbox:use',
  DUTY_WRITE_MATCH: 'duty:write_match',
  DUTY_WRITE_GLOBAL: 'duty:write_global',
  CASE_REVIEW: 'case:review',
  CONFIG_MANAGE: 'config:manage',
  AUDIT_READ: 'audit:read',
  IAM_MANAGE: 'iam:manage',
} as const;
