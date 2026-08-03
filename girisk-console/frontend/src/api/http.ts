import type { ApiResponse } from '../types';
import { clearAuth, getToken } from '../auth/session';

function handleAuthFailure(status: number) {
  if (status === 401) {
    clearAuth();
    if (!window.location.pathname.startsWith('/login')) {
      window.location.href = '/login';
    }
    throw new Error('未登录或登录已过期，请重新登录');
  }
}

export async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(`/api/v1${url}`, {
    ...options,
    headers: { ...headers, ...(options?.headers as Record<string, string>) },
  });

  if (!res.ok) {
    handleAuthFailure(res.status);
    let message = `请求失败 (${res.status})`;
    try {
      const json: ApiResponse<unknown> = await res.json();
      if (json.message) message = json.message;
    } catch {
      /* ignore */
    }
    if (res.status === 403) {
      throw new Error(message || '无权访问该资源');
    }
    throw new Error(message);
  }

  const json: ApiResponse<T> = await res.json();
  if (!json.success) throw new Error(json.message || '请求失败');
  return json.data;
}
