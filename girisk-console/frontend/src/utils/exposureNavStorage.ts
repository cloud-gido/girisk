export type ExposureLevel = 'overall' | 'sport' | 'league' | 'match';

export type ExposureNavState = {
  level: ExposureLevel;
  sport?: string;
  league?: string;
  match?: string;
  filter?: string;
};

const KEY = 'girisk.exposure.nav.v2';

export function readExposureNav(): ExposureNavState | null {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as ExposureNavState;
    if (!parsed?.level) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function writeExposureNav(state: ExposureNavState) {
  try {
    localStorage.setItem(KEY, JSON.stringify(state));
  } catch {
    /* ignore quota */
  }
}
