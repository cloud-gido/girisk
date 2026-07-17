import type { RiskFixtureView, SportsMatch } from '../types';

/** 物化视图场次 → 敞口赛事库：先比 matchCode/fixtureId，再比对阵 */
export function resolveMatchFromFixture(
  fixture: Pick<RiskFixtureView, 'fixtureId' | 'homeTeam' | 'awayTeam'>,
  matches: SportsMatch[],
): SportsMatch | undefined {
  return matches.find((m) => m.matchCode === fixture.fixtureId)
    || matches.find((m) => m.homeTeam === fixture.homeTeam && m.awayTeam === fixture.awayTeam);
}

export function buildExposureMatchUrl(m: SportsMatch, extra?: Record<string, string | undefined>): string {
  const sp = new URLSearchParams();
  sp.set('sport', m.sportCode || 'football');
  sp.set('league', m.leagueCode || 'UNKNOWN');
  sp.set('match', m.matchCode);
  if (extra) {
    Object.entries(extra).forEach(([k, v]) => {
      if (v) sp.set(k, v);
    });
  }
  return `/girisk/exposure?${sp.toString()}`;
}

export function buildExposurePath(parts: {
  sport?: string;
  league?: string;
  match?: string;
  market?: string;
  filter?: string;
}): string {
  const sp = new URLSearchParams();
  if (parts.sport) sp.set('sport', parts.sport);
  if (parts.league) sp.set('league', parts.league);
  if (parts.match) sp.set('match', parts.match);
  if (parts.market) sp.set('market', parts.market);
  if (parts.filter) sp.set('filter', parts.filter);
  const q = sp.toString();
  return q ? `/girisk/exposure?${q}` : '/girisk/exposure';
}
