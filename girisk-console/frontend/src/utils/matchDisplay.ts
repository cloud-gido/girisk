/** 队名 / 联赛等缺省展示为留白。 */
export function blankLabel(v?: string | null, empty = '—'): string {
  if (v == null || !String(v).trim()) return empty;
  const s = String(v).trim();
  if (s === '-' || s === 'UNKNOWN' || s === '未分组联赛') return empty;
  return s;
}

export function matchupLabel(home?: string | null, away?: string | null, matchCode?: string): string {
  const h = blankLabel(home, '');
  const a = blankLabel(away, '');
  if (h && a) return `${h} vs ${a}`;
  if (h || a) return h || a;
  return matchCode || '—';
}

export const SPORT_META: Record<string, { label: string; emoji: string }> = {
  football: { label: '足球', emoji: '⚽' },
  basketball: { label: '篮球', emoji: '🏀' },
};

export function sportMeta(code?: string | null) {
  const c = code || 'football';
  return SPORT_META[c] ?? { label: c, emoji: '🏅' };
}
