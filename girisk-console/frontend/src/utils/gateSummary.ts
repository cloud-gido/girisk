import type { DecisionGateSummary, DecisionReason, RiskDecisionLog } from '../types';

function parseJson<T>(raw: string | undefined | null, fallback: T): T {
  if (!raw) return fallback;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function num(v: unknown): number | undefined {
  if (v == null) return undefined;
  if (typeof v === 'number' && Number.isFinite(v)) return v;
  const n = Number(v);
  return Number.isFinite(n) ? n : undefined;
}

function str(v: unknown): string {
  return v == null ? '' : String(v);
}

/** 从决策日志拼值班用 Gate1/Gate2 摘要（兼容未落 evidence 的旧数据）。 */
export function buildGateSummary(log: RiskDecisionLog | null | undefined): DecisionGateSummary | null {
  if (!log) return null;
  const evidence = parseJson<Record<string, unknown>>(log.evidenceJson, {});
  const feature = parseJson<Record<string, unknown>>(log.featureSnapshotJson, {});
  const gate1Sel = (evidence.gate1TriggerSelection && typeof evidence.gate1TriggerSelection === 'object')
    ? evidence.gate1TriggerSelection as Record<string, unknown>
    : {};

  const hasEvidence = Object.keys(evidence).length > 0;
  const hasFeatureGates = !!(feature.beforeAccept || feature.trialAfterAccept || feature.worstScore);
  if (!hasEvidence && !hasFeatureGates) return null;

  const before = (feature.beforeAccept && typeof feature.beforeAccept === 'object')
    ? feature.beforeAccept as Record<string, unknown> : {};
  const trial = (feature.trialAfterAccept && typeof feature.trialAfterAccept === 'object')
    ? feature.trialAfterAccept as Record<string, unknown> : {};
  const after = (feature.afterActual && typeof feature.afterActual === 'object')
    ? feature.afterActual as Record<string, unknown> : {};

  return {
    rejectReason: str(evidence.rejectReason) || undefined,
    limitRejected: Boolean(evidence.limitRejected),
    exposureRejected: Boolean(evidence.exposureRejected),
    gate1: {
      selectionLabel: str(gate1Sel.selectionLabel) || str(gate1Sel.selection) || undefined,
      groupKey: str(gate1Sel.groupKey) || undefined,
      proposedPayoutYuan: num(gate1Sel.proposedPayout),
      stakeBeforeYuan: num(gate1Sel.stakeBefore),
      targetAmountYuan: num(gate1Sel.targetAmountBefore),
      maxAllowedYuan: num(gate1Sel.maxAllowedAmountBefore),
      acceptMaxYuan: num(gate1Sel.acceptMaxBefore),
      limitDelta: num(evidence.limitDelta),
      seedPayoutYuan: num(evidence.seedPayoutYuan),
      overLimitBefore: gate1Sel.overLimitBefore == null ? undefined : Boolean(gate1Sel.overLimitBefore),
    },
    gate2: {
      trialWorstLossYuan: num(evidence.trialWorstLossYuan) ?? num(trial.maxBookmakerLossYuan),
      maxWorstLossYuan: num(evidence.maxWorstLossYuan),
      worstScore: str(trial.worstScore) || str(feature.worstScore) || undefined,
      beforeWorstPnlYuan: num(before.worstBookmakerPnlYuan),
      trialWorstPnlYuan: num(trial.worstBookmakerPnlYuan),
      afterWorstPnlYuan: num(after.worstBookmakerPnlYuan),
      exceeded: Boolean(evidence.exposureRejected),
    },
  };
}

export function gateLabelFromReasons(reasonsJson?: string | null): string {
  const reasons = parseJson<DecisionReason[]>(reasonsJson, []);
  const stage = reasons.find((r) => r.stage)?.stage;
  if (stage === 'GATE1_LIMIT') return 'Gate1 限额';
  if (stage === 'GATE2_EXPOSURE') return 'Gate2 敞口';
  if (stage === 'GATE') return '通过';
  return stage || '-';
}

export function formatYuan(v?: number | null): string {
  if (v == null || Number.isNaN(v)) return '-';
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
