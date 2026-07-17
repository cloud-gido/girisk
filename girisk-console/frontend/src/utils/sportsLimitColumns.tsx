import { InputNumber } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { OutcomeLimitRow } from '../types';
import { selectionLabel } from './sportsLabels';

interface OutcomeLimitColumnOptions {
  line?: string;
  marketType?: string;
  editable?: boolean;
  onStakeChange?: (selection: string, value: number | null) => void;
}

export function outcomeLimitColumns(options: OutcomeLimitColumnOptions = {}): ColumnsType<OutcomeLimitRow> {
  const { line, marketType, editable, onStakeChange } = options;

  return [
    { title: '盘口', dataIndex: 'selection', render: (v: string) => selectionLabel(v, line, marketType) },
    {
      title: '已投注',
      dataIndex: 'stake',
      width: editable ? 120 : 90,
      render: (v: number, row) =>
        editable ? (
          <InputNumber
            size="small"
            min={0}
            step={100}
            value={v}
            style={{ width: '100%' }}
            onChange={(val) => onStakeChange?.(row.selection, val)}
          />
        ) : (
          v
        ),
    },
    { title: '目标金额', dataIndex: 'targetAmount', width: 90 },
    { title: '最大允许', dataIndex: 'maxAllowedAmount', width: 90 },
    {
      title: '还能接收',
      dataIndex: 'acceptMax',
      width: 100,
      render: (v: number) => <strong style={{ color: v > 0 ? '#1677ff' : '#8c8c8c' }}>{v}</strong>,
    },
  ];
}
