import { request } from './http';

export const riskApi = {
  dashboard: () => request<import('../types').DashboardOverview>('/dashboard/overview'),
  decide: (body: Record<string, unknown>) =>
    request<import('../types').RiskDecisionResponse>('/girisk/decide', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  confirmOrder: (orderId: string) =>
    request<Record<string, unknown>>(`/girisk/orders/${encodeURIComponent(orderId)}/confirm`, { method: 'POST' }),
  cancelOrder: (orderId: string) =>
    request<Record<string, unknown>>(`/girisk/orders/${encodeURIComponent(orderId)}/cancel`, { method: 'POST' }),
  settleOrder: (orderId: string, settlePnlCents?: number) =>
    request<Record<string, unknown>>(`/girisk/orders/${encodeURIComponent(orderId)}/settle`, {
      method: 'POST',
      body: JSON.stringify({ settlePnlCents }),
    }),
  orderStatus: (orderId: string) =>
    request<Record<string, unknown>>(`/girisk/orders/${encodeURIComponent(orderId)}/status`),
  evaluate: (body: import('../types').RiskEvaluateRequest) =>
    request<import('../types').RiskEvaluateResponse>('/girisk/evaluate', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  strategies: () => request<import('../types').RiskStrategy[]>('/strategies'),
  rules: (strategyId?: number) =>
    request<import('../types').RiskRule[]>(strategyId ? `/rules?strategyId=${strategyId}` : '/rules'),
  createRule: (body: Partial<import('../types').RiskRule>) =>
    request<{ id: number }>('/rules', { method: 'POST', body: JSON.stringify(body) }),
  toggleRule: (id: number, enabled: boolean) =>
    request<void>(`/rules/${id}/toggle`, { method: 'PATCH', body: JSON.stringify({ enabled }) }),
  deleteRule: (id: number) => request<void>(`/rules/${id}`, { method: 'DELETE' }),
  lists: (listType?: string) =>
    request<import('../types').RiskListEntry[]>(listType ? `/lists?listType=${listType}` : '/lists'),
  createList: (body: Partial<import('../types').RiskListEntry>) =>
    request<{ id: number }>('/lists', { method: 'POST', body: JSON.stringify(body) }),
  deleteList: (id: number) => request<void>(`/lists/${id}`, { method: 'DELETE' }),
  cases: (status?: string) =>
    request<import('../types').RiskCase[]>(status ? `/cases?status=${status}` : '/cases'),
  reviewCase: (id: number, body: { decision: string; comment?: string; assignee?: string }) =>
    request<import('../types').RiskCase>(`/cases/${id}/review`, { method: 'POST', body: JSON.stringify(body) }),
  decisions: (limit = 50) => request<import('../types').RiskDecisionLog[]>(`/decisions?limit=${limit}`),
  decisionDetail: (id: number) => request<import('../types').RiskDecisionLog>(`/decisions/${id}`),
  decisionsByOrder: (orderId: string) =>
    request<import('../types').RiskDecisionLog[]>(`/decisions/by-order/${encodeURIComponent(orderId)}`),
  replayByOrder: (orderId: string) =>
    request<import('../types').DecisionReplay>(`/replay/order/${encodeURIComponent(orderId)}`),
  replayByTrace: (traceId: string) =>
    request<import('../types').DecisionReplay>(`/replay/trace/${encodeURIComponent(traceId)}`),
  configReleases: () => request<import('../types').RiskConfigRelease[]>('/config/releases'),
  configCurrent: () => request<import('../types').RiskConfigRelease | null>('/config/releases/current'),
  createConfigRelease: (body: Record<string, unknown>) =>
    request<import('../types').RiskConfigRelease>('/config/releases', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  submitConfigRelease: (id: number, actor = 'admin') =>
    request<import('../types').RiskConfigRelease>(`/config/releases/${id}/submit`, {
      method: 'POST',
      body: JSON.stringify({ actor }),
    }),
  approveConfigRelease: (id: number, approvalTicket: string, actor = 'reviewer') =>
    request<import('../types').RiskConfigRelease>(`/config/releases/${id}/approve`, {
      method: 'POST',
      body: JSON.stringify({ actor, approvalTicket }),
    }),
  rejectConfigRelease: (id: number, reason: string, actor = 'reviewer') =>
    request<import('../types').RiskConfigRelease>(`/config/releases/${id}/reject`, {
      method: 'POST',
      body: JSON.stringify({ actor, reason }),
    }),
  publishConfigRelease: (id: number, actor = 'admin') =>
    request<import('../types').RiskConfigRelease>(`/config/releases/${id}/publish`, {
      method: 'POST',
      body: JSON.stringify({ actor }),
    }),
  fixtures: () => request<import('../types').RiskFixtureView[]>('/fixtures'),
  fixturesTop: (limit = 10) => request<import('../types').RiskFixtureView[]>(`/fixtures/top?limit=${limit}`),
  fixture: (fixtureId: string) =>
    request<import('../types').RiskFixtureView>(`/fixtures/${encodeURIComponent(fixtureId)}`),
  events: (limit = 50) => request<import('../types').RiskEvent[]>(`/events?limit=${limit}`),
  streamStatus: () => request<import('../types').StreamStatus>('/stream/status'),
  mockOrder: (body?: Record<string, unknown>) =>
    request<import('../types').RiskEvaluateResponse | { via: string; orderId: string; topic?: string; message?: string }>(
      '/stream/mock-order',
      { method: 'POST', body: JSON.stringify(body || {}) },
    ),
  mockBurst: (count = 5) =>
    request<{ sent: number; via: string }>('/stream/mock-burst', {
      method: 'POST',
      body: JSON.stringify({ count }),
    }),
};
