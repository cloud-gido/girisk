import { Table, Tag, Timeline, message } from 'antd';
import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { RiskEvaluateResponse, RiskEvent } from '../types';
import { DecisionTag } from '../utils/tags';

const severityColor: Record<string, string> = { INFO: 'blue', WARN: 'orange', ERROR: 'red' };

export default function EventsPage() {
  const [events, setEvents] = useState<RiskEvent[]>([]);
  const [liveFeed, setLiveFeed] = useState<RiskEvaluateResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.events(100).then(setEvents).finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    const source = new EventSource('/api/v1/stream/events');
    source.addEventListener('decision', (e) => {
      try {
        const data = JSON.parse(e.data) as RiskEvaluateResponse;
        setLiveFeed((prev) => [data, ...prev].slice(0, 20));
        message.info(`实时决策: ${data.orderId} → ${data.decision}`, 2);
      } catch { /* ignore */ }
    });
    return () => source.close();
  }, []);

  const columns = [
    { title: '时间', dataIndex: 'createdAt', width: 180 },
    { title: '类型', dataIndex: 'eventType', width: 120, render: (v: string) => <Tag>{v}</Tag> },
    { title: '级别', dataIndex: 'severity', width: 80, render: (v: string) => <Tag color={severityColor[v]}>{v}</Tag> },
    { title: '标题', dataIndex: 'title', width: 180 },
    { title: '订单', dataIndex: 'orderId', width: 160 },
    { title: '详情', dataIndex: 'detail', ellipsis: true },
  ];

  return (
    <>
      <div className="page-header">
        <h2>事件监控</h2>
        <p>实时 SSE 推送 + 历史事件，监控风控决策流</p>
      </div>
      {liveFeed.length > 0 && (
        <Timeline style={{ marginBottom: 24 }} items={liveFeed.slice(0, 6).map((d) => ({
          color: d.decision === 'REJECT' ? 'red' : d.decision === 'REVIEW' ? 'orange' : 'green',
          children: <span><DecisionTag value={d.decision} /> {d.orderId} — {d.reason}</span>,
        }))} />
      )}
      <Table className="content-card" rowKey="id" loading={loading} columns={columns} dataSource={events} scroll={{ x: 1000 }} />
    </>
  );
}
