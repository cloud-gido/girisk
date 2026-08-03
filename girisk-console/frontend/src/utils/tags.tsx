import { Tag } from 'antd';

export const decisionColor: Record<string, string> = {
  PASS: 'success',
  REJECT: 'error',
  REVIEW: 'warning',
  LIMIT: 'gold',
  CHALLENGE: 'processing',
};

export const decisionLabel: Record<string, string> = {
  PASS: '通过',
  REJECT: '拒绝',
  REVIEW: '人工审核',
  LIMIT: '限额可接',
  CHALLENGE: '挑战验证',
};

export const sportsDecisionLabel: Record<string, string> = {
  PASS: '接单',
  REJECT: '拒单',
  LIMIT: '限额可接',
};

export const levelColor: Record<string, string> = {
  LOW: 'green',
  MEDIUM: 'gold',
  HIGH: 'orange',
  CRITICAL: 'red',
};

export function DecisionTag({ value, variant = 'risk' }: { value: string; variant?: 'risk' | 'sports' }) {
  const labels = variant === 'sports' ? sportsDecisionLabel : decisionLabel;
  return <Tag color={decisionColor[value] || 'default'}>{labels[value] || value}</Tag>;
}

export function LevelTag({ value }: { value: string }) {
  return <Tag color={levelColor[value] || 'default'}>{value}</Tag>;
}
