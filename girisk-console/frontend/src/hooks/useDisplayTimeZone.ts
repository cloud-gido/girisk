import { useEffect, useState } from 'react';
import { getUserPrefs, type UserPrefs } from '../auth/userPrefs';
import { DEFAULT_DISPLAY_TIMEZONE } from '../utils/timezones';

/** 订阅个性化偏好里的展示时区；切换后各页时间列立即刷新。 */
export function useDisplayTimeZone(): string {
  const [tz, setTz] = useState(() => getUserPrefs().timezone || DEFAULT_DISPLAY_TIMEZONE);

  useEffect(() => {
    const onPrefs = (ev: Event) => {
      const detail = (ev as CustomEvent<UserPrefs>).detail;
      setTz(detail?.timezone || getUserPrefs().timezone || DEFAULT_DISPLAY_TIMEZONE);
    };
    window.addEventListener('girisk-prefs', onPrefs);
    return () => window.removeEventListener('girisk-prefs', onPrefs);
  }, []);

  return tz;
}
