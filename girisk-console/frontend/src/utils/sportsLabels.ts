const SELECTION_LABEL: Record<string, string> = {
  home: '主胜',
  draw: '平局',
  away: '客胜',
  over: 'Over',
  under: 'Under',
};

const SELECTION_SHORT: Record<string, string> = {
  home: '主胜',
  draw: '平',
  away: '客胜',
  over: '大',
  under: '小',
};

function formatSignedLine(value: number): string {
  if (Number.isInteger(value)) {
    return value > 0 ? `+${value}` : String(value);
  }
  const formatted = value.toFixed(2).replace(/0+$/, '').replace(/\.$/, '');
  return value > 0 ? `+${formatted}` : formatted;
}

function parseLineValue(line?: string): number {
  if (!line?.trim()) return 0;
  const v = Number.parseFloat(line.trim());
  return Number.isFinite(v) ? v : 0;
}

export function selectionShortLabel(key: string) {
  return SELECTION_SHORT[key] || selectionLabel(key);
}

/** @param marketType 区分 1X2 与让球下的 home/away */
export function selectionLabel(key: string, line?: string, marketType?: string) {
  if (marketType === 'HANDICAP' && line) {
    const homeLine = parseLineValue(line);
    if (key === 'home') return formatSignedLine(homeLine);
    if (key === 'away') return formatSignedLine(-homeLine);
  }

  const base = SELECTION_LABEL[key] || key;
  if (line && (key === 'over' || key === 'under')) {
    return `${base} ${line}`;
  }
  return base;
}
