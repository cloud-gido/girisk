/** 展示用时区选项（含巴西 / 伦敦 / 北京）。 */
export const DISPLAY_TIMEZONES = [
  { label: '北京 · Asia/Shanghai (UTC+8)', value: 'Asia/Shanghai' },
  { label: '伦敦 · Europe/London', value: 'Europe/London' },
  { label: '巴西 · America/Sao_Paulo', value: 'America/Sao_Paulo' },
  { label: '纽约 · America/New_York', value: 'America/New_York' },
  { label: '东京 · Asia/Tokyo (UTC+9)', value: 'Asia/Tokyo' },
  { label: '新加坡 · Asia/Singapore (UTC+8)', value: 'Asia/Singapore' },
  { label: 'UTC', value: 'UTC' },
] as const;

export type DisplayTimeZone = (typeof DISPLAY_TIMEZONES)[number]['value'];

export const DEFAULT_DISPLAY_TIMEZONE: DisplayTimeZone = 'Asia/Shanghai';

export function timezoneShortLabel(tz: string): string {
  const hit = DISPLAY_TIMEZONES.find((t) => t.value === tz);
  if (hit) {
    return hit.label.split('·')[0].trim();
  }
  return tz || '北京';
}

/** 相对 UTC 的偏移文案，如 UTC+8 / UTC-3 */
export function timezoneOffsetLabel(timeZone: string, at: Date = new Date()): string {
  try {
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone: timeZone || 'Asia/Shanghai',
      timeZoneName: 'shortOffset',
    }).formatToParts(at);
    const raw = parts.find((p) => p.type === 'timeZoneName')?.value || 'UTC';
    return raw.replace(/^GMT/, 'UTC').replace('UTCUTC', 'UTC');
  } catch {
    return 'UTC';
  }
}
