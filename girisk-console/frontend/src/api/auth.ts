import type { ApiResponse } from '../types';
import { setAuth, type SessionUser } from '../auth/session';
import { request } from './http';

export type AuthPayload = {
  token: string;
  username: string;
  displayName: string;
  role: string;
  roles?: string[];
  permissions?: string[];
  operatorScope?: string;
};

export async function login(username: string, password: string) {
  const res = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const json: ApiResponse<AuthPayload> = await res.json();
  if (!json.success) throw new Error(json.message || '登录失败');
  const d = json.data;
  setAuth(
    d.token,
    d.username,
    d.role,
    d.displayName,
    d.roles ?? [d.role],
    d.permissions ?? [],
    d.operatorScope ?? '*',
  );
  return d;
}

export function me() {
  return request<SessionUser>('/auth/me');
}

/** 修改当前登录用户密码；成功后应退出重新登录 */
export async function changePassword(currentPassword: string, newPassword: string) {
  return request<{ status: string }>('/auth/change-password', {
    method: 'POST',
    body: JSON.stringify({ currentPassword, newPassword }),
  });
}

/** 服务端吊销 jti；失败也清本地会话 */
export async function logout() {
  try {
    await request('/auth/logout', { method: 'POST' });
  } catch {
    // ignore
  }
}
