import {
  DEFAULT_DISPLAY_TIMEZONE,
  type DisplayTimeZone,
} from '../utils/timezones';

/** 本地个性化偏好（不进服务端） */
const PREFS_KEY = 'girisk_user_prefs';

export type Density = 'comfortable' | 'compact';
export type LandingPath = '/girisk' | '/girisk/exposure' | '/girisk/stream';
/** 界面主题（对齐 gido「界面与背景」） */
export type AppearanceTheme = 'classic' | 'warm' | 'cool' | 'mint';

export const APPEARANCE_THEMES: { value: AppearanceTheme; label: string }[] = [
  { value: 'classic', label: '清爽经典（默认）' },
  { value: 'warm', label: '护眼暖纸' },
  { value: 'cool', label: '冷雾蓝灰' },
  { value: 'mint', label: '薄荷清水' },
];

/** 头像预设色（本地） */
export const AVATAR_PRESETS = [
  { id: 'teal', color: 'hsl(172 48% 36%)' },
  { id: 'blue', color: 'hsl(210 55% 42%)' },
  { id: 'indigo', color: 'hsl(245 45% 48%)' },
  { id: 'rose', color: 'hsl(350 48% 46%)' },
  { id: 'amber', color: 'hsl(32 70% 44%)' },
  { id: 'violet', color: 'hsl(275 42% 46%)' },
  { id: 'slate', color: 'hsl(215 18% 40%)' },
  { id: 'forest', color: 'hsl(145 40% 34%)' },
] as const;

export type UserPrefs = {
  density: Density;
  landingPath: LandingPath;
  /** IANA 时区，订单/决策时间按此时区展示 */
  timezone: DisplayTimeZone | string;
  appearance: AppearanceTheme;
  /** 头像色板 id；空则按用户名派生 */
  avatarPresetId?: string;
};

const DEFAULTS: UserPrefs = {
  density: 'comfortable',
  landingPath: '/girisk',
  timezone: DEFAULT_DISPLAY_TIMEZONE,
  appearance: 'classic',
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
  if (/[\u4e00-\u9fff]/.test(src)) {
    return src.slice(-1);
  }
  return src.charAt(0).toUpperCase();
}

export function avatarHue(seed?: string): string {
  const s = seed || 'g';
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
  const hue = 160 + (h % 30);
  return `hsl(${hue} 48% 42%)`;
}

export function resolveAvatarColor(username?: string, displayName?: string, presetId?: string): string {
  if (presetId) {
    const hit = AVATAR_PRESETS.find((p) => p.id === presetId);
    if (hit) return hit.color;
  }
  return avatarHue(username || displayName);
}
