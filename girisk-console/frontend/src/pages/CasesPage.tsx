import { Button, Descriptions, Drawer, Input, Modal, Space, Table, Tag, message } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { RiskCase } from '../types';
import { LevelTag } from '../utils/tags';

export default function CasesPage() {
  const [cases, setCases] = useState<RiskCase[]>([]);
  const [loading, setLoading] = useState(true);
  const [reviewId, setReviewId] = useState<number | null>(null);
  const [comment, setComment] = useState('');
  const [detail, setDetail] = useState<RiskCase | null>(null);
  const navigate = useNavigate();

  const load = () => api.cases().then(setCases).finally(() => setLoading(false));
  useEffect(() => { load(); }, []);

  const submitReview = async (decision: string) => {
    if (!reviewId) return;
    const updated = await api.reviewCase(reviewId, { decision, comment, assignee: 'admin' });
    message.success(`审核完成，已回传交易（建议状态 ${decision === 'APPROVED' ? 'CONFIRMED' : 'REJECTED'}）`);
    setReviewId(null);
    setComment('');
    setDetail(updated);
    load();
  };

  const columns = [
    { title: '工单号', dataIndex: 'caseNo', width: 170 },
    {
      title: '订单号',
      dataIndex: 'orderId',
      width: 160,
      render: (v: string) => (
        <Button type="link" size="small" onClick={() => navigate(`/girisk/replay?orderId=${encodeURIComponent(v)}`)}>{v}</Button>
      ),
    },
    { title: '用户ID', dataIndex: 'userId', width: 110 },
    { title: '风险分', dataIndex: 'riskScore', width: 80 },
    { title: '等级', dataIndex: 'riskLevel', width: 90, render: (v: string) => <LevelTag value={v} /> },
    { title: '优先级', dataIndex: 'priority', width: 90, render: (v: string) => <Tag color={v === 'URGENT' ? 'red' : 'orange'}>{v}</Tag> },
    { title: '状态', dataIndex: 'status', width: 100, render: (v: string) => <Tag color={v === 'PENDING' ? 'processing' : v === 'APPROVED' ? 'success' : 'error'}>{v}</Tag> },
    {
      title: '交易回调',
      dataIndex: 'callbackStatus',
      width: 100,
      render: (v: string) => <Tag color={v === 'SENT' ? 'green' : 'default'}>{v || 'NONE'}</Tag>,
    },
    { title: 'SLA', dataIndex: 'slaDeadline', width: 170 },
    {
      title: '操作',
      width: 180,
      render: (_: unknown, r: RiskCase) => (
        <Space>
          <Button type="link" size="small" onClick={() => setDetail(r)}>详情</Button>
          {r.status === 'PENDING' && (
            <Button type="link" size="small" onClick={() => setReviewId(r.id)}>审核</Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <div className="page-header">
        <h2>审核中心</h2>
        <p>人工复核高风险订单；审核结论自动回传交易（CONFIRMED / REJECTED）</p>
      </div>
      <Table className="content-card" rowKey="id" loading={loading} columns={columns} dataSource={cases} scroll={{ x: 1300 }} />
      <Modal title="工单审核" open={reviewId !== null} onCancel={() => setReviewId(null)} footer={[
        <Button key="reject" danger onClick={() => submitReview('REJECTED')}>拒绝</Button>,
        <Button key="approve" type="primary" onClick={() => submitReview('APPROVED')}>通过</Button>,
      ]}>
        <Input.TextArea rows={4} placeholder="审核备注" value={comment} onChange={(e) => setComment(e.target.value)} />
      </Modal>
      <Drawer title={`工单 ${detail?.caseNo || ''}`} width={560} open={!!detail} onClose={() => setDetail(null)}>
        {detail && (
          <>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="订单">{detail.orderId}</Descriptions.Item>
              <Descriptions.Item label="用户">{detail.userId}</Descriptions.Item>
              <Descriptions.Item label="状态">{detail.status}</Descriptions.Item>
              <Descriptions.Item label="审核结论">{detail.reviewDecision || '-'}</Descriptions.Item>
              <Descriptions.Item label="备注">{detail.reviewComment || '-'}</Descriptions.Item>
              <Descriptions.Item label="回调状态">{detail.callbackStatus || 'NONE'}</Descriptions.Item>
              <Descriptions.Item label="回调时间">{detail.callbackAt || '-'}</Descriptions.Item>
            </Descriptions>
            {detail.callbackPayload && (
              <>
                <h4 style={{ marginTop: 16 }}>回传交易 Payload</h4>
                <pre style={{ fontSize: 12, background: '#f5f7fa', padding: 12, borderRadius: 6, overflow: 'auto' }}>
                  {tryPretty(detail.callbackPayload)}
                </pre>
              </>
            )}
          </>
        )}
      </Drawer>
    </>
  );
}

function tryPretty(raw: string) {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}
