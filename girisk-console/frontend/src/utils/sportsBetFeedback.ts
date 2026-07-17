import { notification } from 'antd';
import type { SportsBetEvaluateResponse } from '../types';
import { selectionLabel } from './sportsLabels';

type SportsRejectKind = 'STAKE_FULL' | 'AMOUNT_EXCEED' | 'OTHER';

function resolveRejectKind(res: SportsBetEvaluateResponse): SportsRejectKind {
  if (res.maxAcceptAmount <= 0 || res.reason.includes('已达上限') || res.reason.includes('可接收金额 0')) {
    return 'STAKE_FULL';
  }
  if (res.reason.includes('超过可接收上限')) {
    return 'AMOUNT_EXCEED';
  }
  return 'OTHER';
}

const REJECT_COPY: Record<SportsRejectKind, { title: string; description: (res: SportsBetEvaluateResponse) => string }> = {
  STAKE_FULL: {
    title: '拒单 · 盘口限额已满',
    description: (res) => {
      const sel = res.reason.match(/盘口\s+(\S+)/)?.[1];
      const label = sel ? selectionLabel(sel) : '所选方向';
      return `当前盘口（${label}）已达等比例限额上限，b_max = 0。请更换投注方向或选择其他盘口。`;
    },
  },
  AMOUNT_EXCEED: {
    title: '拒单 · 投注金额超限',
    description: (res) =>
      `本次投注 ${res.amount} 超过可接收上限 ${res.maxAcceptAmount}。请将金额调整至 b_max 以内后重试。`,
  },
  OTHER: {
    title: '拒单 · 风控拦截',
    description: (res) => res.reason || '投注未通过限额评估，请检查参数后重试。',
  },
};

const PASS_COPY = {
  NORMAL: {
    title: '接单 · 正常受理',
    description: '赛事敞口未超阈值，当前不限额模式，订单可正常受理。',
  },
  LIMITED: {
    title: '接单 · 限额内通过',
    description: (res: SportsBetEvaluateResponse) =>
      `限额模式：本次投注 ${res.amount}，该盘口可接收上限 ${res.maxAcceptAmount}，评估通过。`,
  },
};

export function notifySportsBetDecision(res: SportsBetEvaluateResponse) {
  if (res.decision === 'PASS') {
    const copy = res.limitMode ? PASS_COPY.LIMITED : PASS_COPY.NORMAL;
    notification.success({
      message: copy.title,
      description: typeof copy.description === 'function' ? copy.description(res) : copy.description,
      duration: 4,
      placement: 'top',
    });
    return;
  }

  if (res.decision === 'REJECT') {
    const kind = resolveRejectKind(res);
    const copy = REJECT_COPY[kind];
    notification.error({
      message: copy.title,
      description: copy.description(res),
      duration: 5,
      placement: 'top',
    });
  }
}
