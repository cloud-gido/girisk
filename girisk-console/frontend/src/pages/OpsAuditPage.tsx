import { ReloadOutlined } from '@ant-design/icons';
import { Button, Card, Space, Table, Tag, Typography, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { request } from '../api/http';
import type { RiskEvent } from '../types';

const TYPE_COLOR: Record<string, string> = {
  IAM: 'purple',
  DUTY: 'blue',
  AUTH: 'cyan',
};

function typePrefix(t: string) {
  if (t.startsWith('IAM_')) return 'IAM';
  if (t.startsWith('DUTY_')) return 'DUTY';
  if (t.startsWith('AUTH_')) return 'AUTH';
  return 'OTHER';
}

export default function OpsAuditPage() {
  const [rows, setRows] = useState<RiskEvent[]>([]);
  const [loading, setLoading] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const data = await request<RiskEvent[]>('/ops-audit?limit=200');
      setRows(data);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            操作审计
          </Typography.Title>
          <Typography.Paragraph type="secondary" style={{ margin: '4px 0 0' }}>
            账号变更、值班写（限额/闸门/停盘）、登录登出。操作人在「操作者」列。
          </Typography.Paragraph>
        </div>
        <Button icon={<ReloadOutlined />} onClick={() => void reload()} loading={loading}>
          刷新
        </Button>
      </div>
      <Card>
        <Table
          rowKey="id"
          loading={loading}
          dataSource={rows}
          size="small"
          scroll={{ x: 1100 }}
          pagination={{ pageSize: 20, showSizeChanger: true }}
          columns={[
            { title: '时间', dataIndex: 'createdAt', width: 180 },
            {
              title: '类型',
              dataIndex: 'eventType',
              width: 180,
              render: (v: string) => (
                <Tag color={TYPE_COLOR[typePrefix(v)] || 'default'}>{v}</Tag>
              ),
            },
            { title: '操作者', dataIndex: 'userId', width: 120 },
            { title: '标题', dataIndex: 'title', width: 220 },
            { title: '详情', dataIndex: 'detail', ellipsis: true },
          ]}
        />
      </Card>
    </Space>
  );
}
