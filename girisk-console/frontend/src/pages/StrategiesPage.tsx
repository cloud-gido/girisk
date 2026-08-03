import { Badge, Table, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { RiskStrategy } from '../types';

export default function StrategiesPage() {
  const [strategies, setStrategies] = useState<RiskStrategy[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.strategies().then(setStrategies).finally(() => setLoading(false));
  }, []);

  const columns = [
    { title: '策略编码', dataIndex: 'code', width: 180 },
    { title: '策略名称', dataIndex: 'name', width: 180 },
    { title: '场景', dataIndex: 'scenario', width: 120, render: (v: string) => <Tag color="blue">{v}</Tag> },
    { title: '优先级', dataIndex: 'priority', width: 90 },
    { title: '状态', dataIndex: 'enabled', width: 90, render: (v: boolean) => v ? <Badge status="success" text="启用" /> : <Badge status="default" text="禁用" /> },
    { title: '描述', dataIndex: 'description', ellipsis: true },
  ];

  return (
    <>
      <div className="page-header">
        <h2>策略配置</h2>
        <p>按业务场景组织规则集合，支持多策略优先级编排</p>
      </div>
      <Table className="content-card" rowKey="id" loading={loading} columns={columns} dataSource={strategies} pagination={false} />
    </>
  );
}
