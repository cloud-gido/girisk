import type { ApiResponse } from '../types';
import { setAuth } from '../auth/session';
import { request } from './http';

export async function login(username: string, password: string) {
  const res = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const json: ApiResponse<{ token: string; username: string; displayName: string; role: string }> = await res.json();
  if (!json.success) throw new Error(json.message || '登录失败');
  setAuth(json.data.token, json.data.username, json.data.role, json.data.displayName);
  return json.data;
}

export function me() {
  return request<{ username: string; displayName: string; role: string }>('/auth/me');
}
