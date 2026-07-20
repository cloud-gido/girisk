import { Button, Input, Space, Table, Typography, message } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import DecisionDetailDrawer from '../components/DecisionDetailDrawer';
import type { RiskDecisionLog } from '../types';
import { gateLabelFromReasons } from '../utils/gateSummary';
import { DecisionTag, LevelTag } from '../utils/tags';

const POLL_MS = 5000;

export default function DecisionsPage() {
  const [logs, setLogs] = useState<RiskDecisionLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<RiskDecisionLog | null>(null);
  const [orderFilter, setOrderFilter] = useState('');
  const [autoRefresh, setAutoRefresh] = useState(true);
  const filtering = useRef(false);
  const navigate = useNavigate();

  const loadRecent = useCallback(async (silent = false) => {
    if (filtering.current) return;
    if (!silent) setLoading(true);
    try {
      const list = await api.decisions(100);
      setLogs(list);
    } finally {
      if (!silent) setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRecent(false);
  }, [loadRecent]);

  useEffect(() => {
    if (!autoRefresh) return undefined;
    const id = window.setInterval(() => {
      if (document.visibilityState === 'hidden') return;
      loadRecent(true);
    }, POLL_MS);
    return () => window.clearInterval(id);
  }, [autoRefresh, loadRecent]);

  const openDetail = async (row: RiskDecisionLog) => {
    try {
      const detail = await api.decisionDetail(row.id);
      setSelected(detail);
    } catch {
      setSelected(row);
    }
  };

  const searchOrder = async () => {
    if (!orderFilter.trim()) {
      message.warning('请输入订单号');
      return;
    }
    filtering.current = true;
    setLoading(true);
    try {
      const list = await api.decisionsByOrder(orderFilter.trim());
      setLogs(list);
      if (list.length === 0) message.info('未找到该订单决策');
    } finally {
      setLoading(false);
    }
  };

  const resetList = () => {
    filtering.current = false;
    setOrderFilter('');
    loadRecent(false);
  };

  const columns = [
    { title: '时间', dataIndex: 'createdAt', width: 170 },
    {
      title: '订单号',
      dataIndex: 'orderId',
      width: 170,
      render: (v: string, r: RiskDecisionLog) => (
        <Button type="link" size="small" onClick={() => openDetail(r)}>{v}</Button>
      ),
    },
    { title: '用户', dataIndex: 'userId', width: 100 },
    {
      title: '场次',
      dataIndex: 'fixtureId',
      width: 110,
      render: (v: string) => v ? (
        <Button type="link" size="small" style={{ padding: 0 }} onClick={() => navigate(`/girisk/exposure?match=${encodeURIComponent(v)}`)}>
          {v}
        </Button>
      ) : '-',
    },
    { title: '金额', dataIndex: 'amount', width: 90 },
    { title: '决策', dataIndex: 'decision', width: 100, render: (v: string) => <DecisionTag value={v} /> },
    {
      title: '闸门',
      width: 110,
      render: (_: unknown, r: RiskDecisionLog) => (
        <Typography.Text type="secondary">{gateLabelFromReasons(r.reasonsJson)}</Typography.Text>
      ),
    },
    { title: '风险分', dataIndex: 'riskScore', width: 80 },
    { title: '等级', dataIndex: 'riskLevel', width: 90, render: (v: string) => <LevelTag value={v} /> },
    { title: '来源', dataIndex: 'source', width: 80 },
    { title: '原因', dataIndex: 'reason', ellipsis: true },
    {
      title: '操作',
      width: 140,
      fixed: 'right' as const,
      render: (_: unknown, r: RiskDecisionLog) => (
        <Space>
          <Button type="link" size="small" onClick={() => openDetail(r)}>详情</Button>
          <Button type="link" size="small" onClick={() => navigate(`/girisk/replay?orderId=${encodeURIComponent(r.orderId)}`)}>回放</Button>
        </Space>
      ),
    },
  ];

  return (
    <>
      <div className="page-header">
        <h2>决策中心</h2>
        <p>生产决策台账（decision.v1）· 近实时刷新 · 详情看 Gate1 / Gate2 与命中规则</p>
      </div>
      <Space style={{ marginBottom: 12 }} wrap>
        <Input
          placeholder="按订单号筛选"
          value={orderFilter}
          onChange={(e) => setOrderFilter(e.target.value)}
          onPressEnter={searchOrder}
          style={{ width: 240 }}
          allowClear
        />
        <Button onClick={searchOrder}>查询</Button>
        <Button onClick={resetList}>重置</Button>
        <Button type={autoRefresh ? 'primary' : 'default'} onClick={() => setAutoRefresh((v) => !v)}>
          {autoRefresh ? '自动刷新 · 5s' : '已暂停刷新'}
        </Button>
      </Space>
      <Table
        className="content-card"
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={logs}
        scroll={{ x: 1500 }}
        pagination={{ pageSize: 15 }}
        onRow={(r) => ({ onDoubleClick: () => openDetail(r) })}
      />
      <DecisionDetailDrawer open={!!selected} onClose={() => setSelected(null)} log={selected} />
    </>
  );
}
