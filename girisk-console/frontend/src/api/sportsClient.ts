import { request } from './http';

export const sportsApi = {
  dashboard: () => request<import('../types').SportsDashboardSummary>('/sports/dashboard'),
  matches: () => request<import('../types').SportsMatch[]>('/sports/matches'),
  match: (code: string) => request<import('../types').SportsMatchView>(`/sports/matches/${code}`),
  exposureCheck: (code: string) =>
    request<import('../types').SportsMatchView>(`/sports/matches/${code}/exposure-check`, { method: 'POST' }),
  getLimitOverride: (code: string) =>
    request<import('../types').FixtureLimitParamsView>(`/sports/matches/${code}/limit-override`),
  putLimitOverride: (code: string, body: import('../types').FixtureLimitOverrideRequest) =>
    request<import('../types').FixtureLimitParamsView>(`/sports/matches/${code}/limit-override`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  clearLimitOverride: (code: string) =>
    request<import('../types').FixtureLimitParamsView>(`/sports/matches/${code}/limit-override`, {
      method: 'DELETE',
    }),
  setMatchStatus: (code: string, status: 'ACTIVE' | 'SUSPENDED') =>
    request<import('../types').SportsMatchView>(`/sports/matches/${code}/status`, {
      method: 'POST',
      body: JSON.stringify({ status }),
    }),
  loadDemoReplay: (force = false) =>
    request<Record<string, unknown>>(`/sports/replay/demo?force=${force}`, { method: 'POST' }),
  getScopeOverride: (scopeType: 'overall' | 'sport', scopeKey: string) =>
    request<import('../types').ScopeLimitParamsView>(
      `/sports/scopes/${scopeType}/${encodeURIComponent(scopeKey)}/limit-override`,
    ),
  putScopeOverride: (
    scopeType: 'overall' | 'sport',
    scopeKey: string,
    body: import('../types').FixtureLimitOverrideRequest,
  ) =>
    request<import('../types').ScopeLimitParamsView>(
      `/sports/scopes/${scopeType}/${encodeURIComponent(scopeKey)}/limit-override`,
      { method: 'PUT', body: JSON.stringify(body) },
    ),
  clearScopeOverride: (scopeType: 'overall' | 'sport', scopeKey: string) =>
    request<import('../types').ScopeLimitParamsView>(
      `/sports/scopes/${scopeType}/${encodeURIComponent(scopeKey)}/limit-override`,
      { method: 'DELETE' },
    ),
  getLeagueScopeOverride: (sportCode: string, leagueCode: string) =>
    request<import('../types').ScopeLimitParamsView>(
      `/sports/scopes/league/${encodeURIComponent(sportCode)}/${encodeURIComponent(leagueCode)}/limit-override`,
    ),
  putLeagueScopeOverride: (
    sportCode: string,
    leagueCode: string,
    body: import('../types').FixtureLimitOverrideRequest,
  ) =>
    request<import('../types').ScopeLimitParamsView>(
      `/sports/scopes/league/${encodeURIComponent(sportCode)}/${encodeURIComponent(leagueCode)}/limit-override`,
      { method: 'PUT', body: JSON.stringify(body) },
    ),
  clearLeagueScopeOverride: (sportCode: string, leagueCode: string) =>
    request<import('../types').ScopeLimitParamsView>(
      `/sports/scopes/league/${encodeURIComponent(sportCode)}/${encodeURIComponent(leagueCode)}/limit-override`,
      { method: 'DELETE' },
    ),
  getScopeTradingStatus: (scopeType: 'overall' | 'sport', scopeKey: string) =>
    request<import('../types').ScopeTradingStatusSummary>(
      `/sports/scopes/${scopeType}/${encodeURIComponent(scopeKey)}/status`,
    ),
  setScopeTradingStatus: (
    scopeType: 'overall' | 'sport',
    scopeKey: string,
    status: 'ACTIVE' | 'SUSPENDED',
  ) =>
    request<import('../types').ScopeTradingStatusSummary>(
      `/sports/scopes/${scopeType}/${encodeURIComponent(scopeKey)}/status`,
      { method: 'POST', body: JSON.stringify({ status }) },
    ),
  getLeagueTradingStatus: (sportCode: string, leagueCode: string) =>
    request<import('../types').ScopeTradingStatusSummary>(
      `/sports/scopes/league/${encodeURIComponent(sportCode)}/${encodeURIComponent(leagueCode)}/status`,
    ),
  setLeagueTradingStatus: (
    sportCode: string,
    leagueCode: string,
    status: 'ACTIVE' | 'SUSPENDED',
  ) =>
    request<import('../types').ScopeTradingStatusSummary>(
      `/sports/scopes/league/${encodeURIComponent(sportCode)}/${encodeURIComponent(leagueCode)}/status`,
      { method: 'POST', body: JSON.stringify({ status }) },
    ),
  getScopeGates: (scopeType: 'overall' | 'sport', scopeKey: string) =>
    request<import('../types').ScopeGateParamsView>(
      `/sports/scopes/${scopeType}/${encodeURIComponent(scopeKey)}/gates`,
    ),
  putScopeGates: (
    scopeType: 'overall' | 'sport',
    scopeKey: string,
    body: import('../types').ScopeGateOverrideRequest,
  ) =>
    request<import('../types').ScopeGateParamsView>(
      `/sports/scopes/${scopeType}/${encodeURIComponent(scopeKey)}/gates`,
      { method: 'PUT', body: JSON.stringify(body) },
    ),
  clearScopeGates: (scopeType: 'overall' | 'sport', scopeKey: string) =>
    request<import('../types').ScopeGateParamsView>(
      `/sports/scopes/${scopeType}/${encodeURIComponent(scopeKey)}/gates`,
      { method: 'DELETE' },
    ),
  getLeagueGates: (sportCode: string, leagueCode: string) =>
    request<import('../types').ScopeGateParamsView>(
      `/sports/scopes/league/${encodeURIComponent(sportCode)}/${encodeURIComponent(leagueCode)}/gates`,
    ),
  putLeagueGates: (
    sportCode: string,
    leagueCode: string,
    body: import('../types').ScopeGateOverrideRequest,
  ) =>
    request<import('../types').ScopeGateParamsView>(
      `/sports/scopes/league/${encodeURIComponent(sportCode)}/${encodeURIComponent(leagueCode)}/gates`,
      { method: 'PUT', body: JSON.stringify(body) },
    ),
  clearLeagueGates: (sportCode: string, leagueCode: string) =>
    request<import('../types').ScopeGateParamsView>(
      `/sports/scopes/league/${encodeURIComponent(sportCode)}/${encodeURIComponent(leagueCode)}/gates`,
      { method: 'DELETE' },
    ),
  getMatchGates: (matchCode: string) =>
    request<import('../types').ScopeGateParamsView>(`/sports/matches/${encodeURIComponent(matchCode)}/gates`),
  putMatchGates: (matchCode: string, body: import('../types').ScopeGateOverrideRequest) =>
    request<import('../types').ScopeGateParamsView>(`/sports/matches/${encodeURIComponent(matchCode)}/gates`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  clearMatchGates: (matchCode: string) =>
    request<import('../types').ScopeGateParamsView>(`/sports/matches/${encodeURIComponent(matchCode)}/gates`, {
      method: 'DELETE',
    }),
  betEvaluate: (body: Record<string, unknown>) =>
    request<import('../types').SportsBetEvaluateResponse>('/sports/bet/evaluate', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  bets: (matchCode?: string, limit = 50) =>
    request<import('../types').SportsBetLog[]>(
      matchCode ? `/sports/bets?matchCode=${matchCode}&limit=${limit}` : `/sports/bets?limit=${limit}`,
    ),
};
