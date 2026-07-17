export interface BetPreset {
  key: string;
  label: string;
  hint?: string;
  values: Record<string, unknown>;
}

export interface BetPresetGroup {
  title: string;
  presets: BetPreset[];
}

/** 与产品文档数值对齐的测试场景 */
export const BET_PRESET_GROUPS: BetPresetGroup[] = [
  {
    title: '胜负平 · MATCH-001（主10000/平3000/客2000）',
    presets: [
      {
        key: '1x2-home-reject',
        label: '主胜应拒',
        hint: 'b_max=0',
        values: { matchCode: 'MATCH-001', marketType: 'ONE_X_TWO', selection: 'home', amount: 100, dryRun: true },
      },
      {
        key: '1x2-draw-ok',
        label: '平局可接',
        hint: 'b_max=5000',
        values: { matchCode: 'MATCH-001', marketType: 'ONE_X_TWO', selection: 'draw', amount: 3000, dryRun: true },
      },
      {
        key: '1x2-draw-over',
        label: '平局超额拒',
        hint: '金额>5000',
        values: { matchCode: 'MATCH-001', marketType: 'ONE_X_TWO', selection: 'draw', amount: 6000, dryRun: true },
      },
      {
        key: '1x2-away-ok',
        label: '客胜可接',
        hint: 'b_max≈6667',
        values: { matchCode: 'MATCH-001', marketType: 'ONE_X_TWO', selection: 'away', amount: 5000, dryRun: true },
      },
      {
        key: '1x2-away-over',
        label: '客胜超额拒',
        hint: '金额>6667',
        values: { matchCode: 'MATCH-001', marketType: 'ONE_X_TWO', selection: 'away', amount: 7000, dryRun: true },
      },
    ],
  },
  {
    title: '大小球3 · MATCH-001（大10000/小3000）',
    presets: [
      {
        key: 'ou-big-reject',
        label: '大球3应拒',
        hint: 'b_max=0',
        values: { matchCode: 'MATCH-001', marketType: 'OVER_UNDER', line: '3', selection: 'over', amount: 100, dryRun: true },
      },
      {
        key: 'ou-small-ok',
        label: '小球3可接',
        hint: 'b_max=12000',
        values: { matchCode: 'MATCH-001', marketType: 'OVER_UNDER', line: '3', selection: 'under', amount: 5000, dryRun: true },
      },
      {
        key: 'ou-small-over',
        label: '小球超额拒',
        hint: '金额>12000',
        values: { matchCode: 'MATCH-001', marketType: 'OVER_UNDER', line: '3', selection: 'under', amount: 13000, dryRun: true },
      },
    ],
  },
  {
    title: '让球+1 · MATCH-001（主10000/客3000）',
    presets: [
      {
        key: 'hc-home-reject',
        label: '主队+1应拒',
        hint: 'b_max=0',
        values: { matchCode: 'MATCH-001', marketType: 'HANDICAP', line: '1', selection: 'home', amount: 100, dryRun: true },
      },
      {
        key: 'hc-away-ok',
        label: '客队+1可接',
        hint: 'b_max=12000',
        values: { matchCode: 'MATCH-001', marketType: 'HANDICAP', line: '1', selection: 'away', amount: 5000, dryRun: true },
      },
      {
        key: 'hc-away-over',
        label: '客队超额拒',
        hint: '金额>12000',
        values: { matchCode: 'MATCH-001', marketType: 'HANDICAP', line: '1', selection: 'away', amount: 13000, dryRun: true },
      },
    ],
  },
  {
        title: '非限额模式 · MATCH-002（皇马 vs 巴萨）',
        presets: [
          {
            key: 'normal-pass',
            label: '正常接单',
            hint: '总投1700，未超阈',
            values: { matchCode: 'MATCH-002', marketType: 'ONE_X_TWO', selection: 'home', amount: 500, dryRun: true },
          },
        ],
  },
];
