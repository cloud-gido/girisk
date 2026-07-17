import { Switch, Table, Tag, message } from 'antd';
import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { RiskRule } from '../types';
import { DecisionTag } from '../utils/tags';

export default function RulesPage() {
  const [rules, setRules] = useState<RiskRule[]>([]);
  const [loading, setLoading] = useState(true);

  const load = () => api.rules().then(setRules).finally(() => setLoading(false));

  useEffect(() => { load(); }, []);

  const toggle = async (id: number, enabled: boolean) => {
    await api.toggleRule(id, enabled);
    message.success(enabled ? '规则已启用' : '规则已禁用');
    load();
  };

  const columns = [
    { title: '规则编码', dataIndex: 'code', width: 100 },
    { title: '规则名称', dataIndex: 'name', width: 160 },
    { title: '类型', dataIndex: 'ruleType', width: 100, render: (v: string) => <Tag>{v}</Tag> },
    { title: '字段', dataIndex: 'field', width: 120 },
    { title: '条件', render: (_: unknown, r: RiskRule) => `${r.operator || ''} ${r.threshold || ''}` },
    { title: '动作', dataIndex: 'action', width: 100, render: (v: string) => <DecisionTag value={v} /> },
    { title: '权重', dataIndex: 'scoreWeight', width: 70 },
    { title: '优先级', dataIndex: 'priority', width: 80 },
    { title: '状态', dataIndex: 'enabled', width: 80, render: (v: boolean, r: RiskRule) => <Switch checked={v} size="small" onChange={(c) => toggle(r.id, c)} /> },
    { title: '描述', dataIndex: 'description', ellipsis: true },
  ];

  return (
    <>
      <div className="page-header">
        <h2>规则管理</h2>
        <p>配置阈值、名单、复合条件等风控规则，支持优先级与权重评分</p>
      </div>
      <Table className="content-card" rowKey="id" loading={loading} columns={columns} dataSource={rules} pagination={{ pageSize: 10 }} scroll={{ x: 1100 }} />
    </>
  );
}
