import type { OutcomeLimitRow } from '../types';

export interface LimitResult {
  bMax: number;
  targetAmount: number;
  maxAllowed: number;
}

/** 与后端 ProportionalLimitCalculator 一致 */
export function calcLimit(current: number, groupAmounts: number[], delta: number): LimitResult {
  const n = groupAmounts.length;
  const total = groupAmounts.reduce((sum, v) => sum + v, 0);
  const weight = 1 / n;
  const factor = (1 + delta) * weight;
  const denominator = 1 - factor;

  const targetAmount = round2(total * weight);
  const maxAllowed = round2(total * factor);

  let bMax = denominator === 0 ? 0 : (factor * total - current) / denominator;
  bMax = round2(bMax);
  if (bMax < 0) bMax = 0;

  return { bMax, targetAmount, maxAllowed };
}

export function calcAll(groupAmounts: number[], delta: number): LimitResult[] {
  return groupAmounts.map((amount) => calcLimit(amount, groupAmounts, delta));
}

export function buildOutcomeRows(
  selections: string[],
  stakes: Record<string, number>,
  delta: number,
): OutcomeLimitRow[] {
  const amounts = selections.map((sel) => stakes[sel] ?? 0);
  const results = calcAll(amounts, delta);
  return selections.map((selection, i) => ({
    selection,
    stake: amounts[i],
    targetAmount: results[i].targetAmount,
    maxAllowedAmount: results[i].maxAllowed,
    acceptMax: results[i].bMax,
  }));
}

export function groupStakeTotal(stakes: Record<string, number>): number {
  return round2(Object.values(stakes).reduce((sum, v) => sum + v, 0));
}

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}
