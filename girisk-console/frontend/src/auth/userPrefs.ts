/** 本地个性化偏好（不进服务端） */
const PREFS_KEY = 'girisk_user_prefs';

export type Density = 'comfortable' | 'compact';
export type LandingPath = '/girisk' | '/girisk/exposure' | '/girisk/stream';

export type UserPrefs = {
  density: Density;
  landingPath: LandingPath;
};

const DEFAULTS: UserPrefs = {
  density: 'comfortable',
  landingPath: '/girisk',
};

export function getUserPrefs(): UserPrefs {
  try {
    const raw = localStorage.getItem(PREFS_KEY);
    if (!raw) return { ...DEFAULTS };
    return { ...DEFAULTS, ...JSON.parse(raw) };
  } catch {
    return { ...DEFAULTS };
  }
}

export function setUserPrefs(patch: Partial<UserPrefs>): UserPrefs {
  const next = { ...getUserPrefs(), ...patch };
  localStorage.setItem(PREFS_KEY, JSON.stringify(next));
  window.dispatchEvent(new CustomEvent('girisk-prefs', { detail: next }));
  return next;
}

export function avatarInitials(displayName?: string, username?: string): string {
  const src = (displayName || username || '?').trim();
  if (!src) return '?';
  // 中文取末字，英文取首字母
  if (/[\u4e00-\u9fff]/.test(src)) {
    return src.slice(-1);
  }
  return src.charAt(0).toUpperCase();
}

export function avatarHue(seed?: string): string {
  const s = seed || 'g';
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
  // 青绿系 160–190
  const hue = 160 + (h % 30);
  return `hsl(${hue} 48% 42%)`;
}
