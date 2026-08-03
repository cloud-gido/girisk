import {
  Button, Descriptions, Drawer, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, message,
} from 'antd';
import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { RiskConfigRelease } from '../types';

const statusColor: Record<string, string> = {
  DRAFT: 'default',
  PENDING_APPROVAL: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
  PUBLISHED: 'purple',
};

const statusLabel: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_APPROVAL: '待审批',
  APPROVED: '已审批',
  REJECTED: '已驳回',
  PUBLISHED: '已发布',
};

export default function ConfigCenterPage() {
  const [list, setList] = useState<RiskConfigRelease[]>([]);
  const [current, setCurrent] = useState<RiskConfigRelease | null>(null);
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<RiskConfigRelease | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [approveOpen, setApproveOpen] = useState<RiskConfigRelease | null>(null);
  const [ticket, setTicket] = useState('');
  const [form] = Form.useForm();

  const load = async () => {
    setLoading(true);
    try {
      const [releases, pub] = await Promise.all([api.configReleases(), api.configCurrent()]);
      setList(releases);
      setCurrent(pub);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const createDraft = async () => {
    const values = await form.validateFields();
    await api.createConfigRelease({
      createdBy: 'admin',
      changeSummary: values.changeSummary,
      paramSetVersion: values.paramSetVersion,
      ruleSetVersion: values.ruleSetVersion,
      paramSet: {
        version: values.paramSetVersion,
        limit: {
          delta: values.delta,
          basis: 'payout',
          initialSeedPayoutCents: Math.round(values.seedYuan * 100),
          rejectBoundary: 'GTE',
        },
        exposure: {
          maxWorstLossCents: Math.round(values.worstLossYuan * 100),
          grid: { home: 6, away: 6, liveScoreDynamic: true },
        },
        decision: {
          limitDecisionEnabled: values.limitDecisionEnabled,
          unknownPlayTypePolicy: 'REVIEW',
          pendingReserveTtlMs: 30000,
        },
      },
      ruleSet: { version: values.ruleSetVersion, rules: [] },
    });
    message.success('草稿已创建');
    setCreateOpen(false);
    form.resetFields();
    load();
  };

  const columns = [
    { title: 'Epoch', dataIndex: 'configEpoch', width: 80 },
    { title: '参数集', dataIndex: 'paramSetVersion', width: 100 },
    { title: '规则集', dataIndex: 'ruleSetVersion', width: 100 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (v: string) => <Tag color={statusColor[v]}>{statusLabel[v] || v}</Tag>,
    },
    { title: '变更说明', dataIndex: 'changeSummary', ellipsis: true },
    { title: '审批单', dataIndex: 'approvalTicket', width: 150, render: (v: string) => v || '-' },
    { title: '创建人', dataIndex: 'createdBy', width: 90 },
    { title: '创建时间', dataIndex: 'createdAt', width: 170 },
    {
      title: '操作',
      width: 280,
      fixed: 'right' as const,
      render: (_: unknown, r: RiskConfigRelease) => (
        <Space wrap size="small">
          <Button type="link" size="small" onClick={() => setDetail(r)}>详情</Button>
          {(r.status === 'DRAFT' || r.status === 'REJECTED') && (
            <Button type="link" size="small" onClick={async () => {
              await api.submitConfigRelease(r.id, 'admin');
              message.success('已提交审批');
              load();
            }}>提交审批</Button>
          )}
          {r.status === 'PENDING_APPROVAL' && (
            <>
              <Button type="link" size="small" onClick={() => { setApproveOpen(r); setTicket(''); }}>审批通过</Button>
              <Button type="link" size="small" danger onClick={async () => {
                await api.rejectConfigRelease(r.id, '参数不合理', 'reviewer');
                message.success('已驳回');
                load();
              }}>驳回</Button>
            </>
          )}
          {r.status === 'APPROVED' && (
            <Button type="link" size="small" onClick={async () => {
              await api.publishConfigRelease(r.id, 'admin');
              message.success('已发布到 girisk.config.v1');
              load();
            }}>发布</Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <div className="page-header">
        <h2>配置中心</h2>
        <p>参数 / 规则版本化发布：草稿 → 双人审批 → 发布（configEpoch 不可变，决策可回溯）</p>
      </div>

      {current && (
        <div className="content-card" style={{ padding: 16, marginBottom: 16 }}>
          <Space wrap>
            <Tag color="purple">当前生产 Epoch {current.configEpoch}</Tag>
            <span>paramSet {current.paramSetVersion}</span>
            <span>ruleSet {current.ruleSetVersion}</span>
            <span>发布于 {current.publishedAt}</span>
            <span>审批单 {current.approvalTicket}</span>
          </Space>
        </div>
      )}

      <Space style={{ marginBottom: 12 }}>
        <Button type="primary" onClick={() => setCreateOpen(true)}>新建配置草稿</Button>
        <Button onClick={load}>刷新</Button>
      </Space>

      <Table
        className="content-card"
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={list}
        scroll={{ x: 1200 }}
        pagination={{ pageSize: 10 }}
      />

      <Modal
        title="新建配置草稿"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={createDraft}
        width={560}
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{
          paramSetVersion: `ps-v${Date.now().toString().slice(-4)}`,
          ruleSetVersion: 'rs-v12',
          delta: 0.2,
          seedYuan: 2000,
          worstLossYuan: 1000,
          limitDecisionEnabled: true,
          changeSummary: '',
        }}>
          <Form.Item name="changeSummary" label="变更说明" rules={[{ required: true }]}>
            <Input.TextArea rows={2} placeholder="例如：收紧 δ，提高种子" />
          </Form.Item>
          <Form.Item name="paramSetVersion" label="参数集版本" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="ruleSetVersion" label="规则集版本" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="delta" label="限额 δ" rules={[{ required: true }]}>
            <InputNumber min={0.05} max={0.5} step={0.05} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="seedYuan" label="冷启动种子（元，返彩口径）" rules={[{ required: true }]}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="worstLossYuan" label="敞口最差亏损阈值（元）" rules={[{ required: true }]}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="limitDecisionEnabled" label="启用 LIMIT 部分可接">
            <Select options={[{ value: true, label: '启用' }, { value: false, label: '关闭（超限直接 REJECT）' }]} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="审批通过（双人复核）"
        open={!!approveOpen}
        onCancel={() => setApproveOpen(null)}
        onOk={async () => {
          if (!approveOpen) return;
          if (!ticket.trim()) {
            message.warning('请填写审批单号');
            return;
          }
          await api.approveConfigRelease(approveOpen.id, ticket.trim(), 'reviewer');
          message.success('审批通过');
          setApproveOpen(null);
          load();
        }}
      >
        <p>当前版本 Epoch {approveOpen?.configEpoch}，提交人 {approveOpen?.submittedBy}。审批人使用 reviewer 账号，不可与提交人相同。</p>
        <Input placeholder="审批单号，如 RISK-2026-0715-02" value={ticket} onChange={(e) => setTicket(e.target.value)} />
      </Modal>

      <Drawer title={`配置详情 Epoch ${detail?.configEpoch ?? ''}`} width={720} open={!!detail} onClose={() => setDetail(null)}>
        {detail && (
          <>
            <Descriptions column={2} size="small" bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label="状态"><Tag color={statusColor[detail.status]}>{statusLabel[detail.status]}</Tag></Descriptions.Item>
              <Descriptions.Item label="Scope">{detail.scope}</Descriptions.Item>
              <Descriptions.Item label="变更">{detail.changeSummary}</Descriptions.Item>
              <Descriptions.Item label="审批单">{detail.approvalTicket || '-'}</Descriptions.Item>
              <Descriptions.Item label="创建">{detail.createdBy} / {detail.createdAt}</Descriptions.Item>
              <Descriptions.Item label="提交">{detail.submittedBy || '-'} / {detail.submittedAt || '-'}</Descriptions.Item>
              <Descriptions.Item label="审批">{detail.approvedBy || '-'} / {detail.approvedAt || '-'}</Descriptions.Item>
              <Descriptions.Item label="发布">{detail.publishedBy || '-'} / {detail.publishedAt || '-'}</Descriptions.Item>
            </Descriptions>
            <h4>paramSet</h4>
            <pre style={{ fontSize: 12, background: '#f5f7fa', padding: 12, borderRadius: 6, overflow: 'auto' }}>
              {tryPretty(detail.paramSetJson)}
            </pre>
            <h4>ruleSet</h4>
            <pre style={{ fontSize: 12, background: '#f5f7fa', padding: 12, borderRadius: 6, overflow: 'auto' }}>
              {tryPretty(detail.ruleSetJson)}
            </pre>
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
