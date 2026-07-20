import { Alert, Button, Card, Col, Descriptions, Empty, Input, Row, Space, Steps, Table, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import GateSummaryPanel from '../components/GateSummaryPanel';
import type { DecisionReplay } from '../types';
import { buildGateSummary } from '../utils/gateSummary';
import { DecisionTag, LevelTag } from '../utils/tags';

export default function ReplayPage() {
  const [params, setParams] = useSearchParams();
  const [orderId, setOrderId] = useState(params.get('orderId') || '');
  const [traceId, setTraceId] = useState(params.get('traceId') || '');
  const [loading, setLoading] = useState(false);
  const [replay, setReplay] = useState<DecisionReplay | null>(null);

  const load = async (by: 'order' | 'trace', value: string) => {
    if (!value.trim()) {
      message.warning(by === 'order' ? '请输入订单号' : '请输入 traceId');
      return;
    }
    setLoading(true);
    try {
      const data = by === 'order'
        ? await api.replayByOrder(value.trim())
        : await api.replayByTrace(value.trim());
      setReplay(data);
      setParams(by === 'order' ? { orderId: value.trim() } : { traceId: value.trim() });
    } catch (e: unknown) {
      setReplay(null);
      message.error(e instanceof Error ? e.message : '回放失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const oid = params.get('orderId');
    const tid = params.get('traceId');
    if (oid) {
      setOrderId(oid);
      load('order', oid);
    } else if (tid) {
      setTraceId(tid);
      load('trace', tid);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const d = replay?.decision;

  return (
    <>
      <div className="page-header">
        <h2>风险回放</h2>
        <p>生产决策解释：按订单 / Trace 还原 Gate1 限额、Gate2 敞口、规则版本与理由</p>
      </div>

      <Card className="content-card" style={{ marginBottom: 16 }}>
        <Space wrap>
          <Input
            placeholder="订单号，如 ORD-20260715001"
            value={orderId}
            onChange={(e) => setOrderId(e.target.value)}
            onPressEnter={() => load('order', orderId)}
            style={{ width: 260 }}
          />
          <Button type="primary" loading={loading} onClick={() => load('order', orderId)}>按订单回放</Button>
          <Input
            placeholder="traceId，如 tr-7f3a9c"
            value={traceId}
            onChange={(e) => setTraceId(e.target.value)}
            onPressEnter={() => load('trace', traceId)}
            style={{ width: 220 }}
          />
          <Button loading={loading} onClick={() => load('trace', traceId)}>按 Trace 回放</Button>
        </Space>
      </Card>

      {!replay && !loading && <Empty description="输入订单号或 Trace 开始回放" />}

      {replay && d && (
        <>
          <Alert
            type={replay.explainable ? 'success' : 'warning'}
            showIcon
            style={{ marginBottom: 16 }}
            message={replay.explainable ? '本条决策可完整解释与回溯' : '本条决策缺少 reasons/versions 快照（旧数据）'}
            description={
              replay.auditSource
                ? `审计数据源：${replay.auditSource === 'doris' ? 'Doris（Kafka 原样）' : 'PostgreSQL（运营库回退）'}`
                : undefined
            }
          />

          <Steps
            style={{ marginBottom: 24 }}
            items={[
              { title: '请求入库', description: d.createdAt, status: 'finish' },
              {
                title: '规则/闸门判定',
                description: (replay.reasons?.length || 0) > 0
                  ? replay.reasons.map((r) => r.ruleId).join(', ')
                  : '无命中',
                status: 'finish',
              },
              {
                title: `决策 ${d.decision}`,
                description: d.reason,
                status: d.decision === 'REJECT' ? 'error' : d.decision === 'PASS' ? 'finish' : 'process',
              },
              {
                title: replay.case ? `工单 ${replay.case.status}` : '无需人工',
                description: replay.case?.caseNo,
                status: replay.case ? (replay.case.status === 'PENDING' ? 'process' : 'finish') : 'wait',
              },
            ]}
          />

          <Row gutter={[16, 16]}>
            <Col span={24}>
              <Card title="闸门摘要 · Gate1 / Gate2" className="content-card">
                <GateSummaryPanel summary={replay.gateSummary || buildGateSummary(d)} />
              </Card>
            </Col>
            <Col xs={24} lg={12}>
              <Card title="决策结论" className="content-card">
                <Descriptions column={1} size="small">
                  <Descriptions.Item label="订单">{d.orderId}</Descriptions.Item>
                  <Descriptions.Item label="决策"><DecisionTag value={d.decision} /></Descriptions.Item>
                  <Descriptions.Item label="等级"><LevelTag value={d.riskLevel} /></Descriptions.Item>
                  <Descriptions.Item label="原因">{d.reason}</Descriptions.Item>
                  <Descriptions.Item label="traceId">{d.traceId || d.requestId}</Descriptions.Item>
                  <Descriptions.Item label="场次">{d.fixtureId || '-'}</Descriptions.Item>
                  <Descriptions.Item label="商户">{d.operatorId || '-'}</Descriptions.Item>
                </Descriptions>
              </Card>
            </Col>
            <Col xs={24} lg={12}>
              <Card title="版本三元组" className="content-card">
                <Descriptions column={1} size="small">
                  <Descriptions.Item label="configEpoch">{replay.versions?.configEpoch ?? '-'}</Descriptions.Item>
                  <Descriptions.Item label="paramSet">{replay.versions?.paramSetVersion ?? '-'}</Descriptions.Item>
                  <Descriptions.Item label="ruleSet">{replay.versions?.ruleSetVersion ?? '-'}</Descriptions.Item>
                  <Descriptions.Item label="engineBuild">{replay.versions?.engineBuild ?? '-'}</Descriptions.Item>
                </Descriptions>
                {replay.configRelease && (
                  <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
                    已关联配置发布：epoch {replay.configRelease.configEpoch} · {replay.configRelease.changeSummary}
                  </Typography.Paragraph>
                )}
              </Card>
            </Col>
            <Col xs={24} lg={12}>
              <Card title="命中规则与证据" className="content-card">
                {(replay.reasons || []).length === 0 ? (
                  <Empty description="PASS，无命中" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                ) : (
                  <Table
                    size="small"
                    pagination={false}
                    rowKey={(_, i) => String(i)}
                    dataSource={replay.reasons}
                    columns={[
                      { title: '规则', dataIndex: 'ruleId', width: 160 },
                      { title: '阶段', dataIndex: 'stage', width: 120 },
                      { title: '动作', dataIndex: 'action', width: 90, render: (v: string) => <DecisionTag value={v} /> },
                      { title: '说明', dataIndex: 'message' },
                    ]}
                    expandable={{
                      expandedRowRender: (r) => (
                        <pre style={{ margin: 0, fontSize: 12 }}>{JSON.stringify(r.evidence, null, 2)}</pre>
                      ),
                    }}
                  />
                )}
              </Card>
            </Col>
            <Col xs={24} lg={12}>
              <Card title="特征快照 / 盘口" className="content-card">
                <Typography.Text type="secondary">featureSnapshot</Typography.Text>
                <pre style={{ fontSize: 12, background: '#f5f7fa', padding: 12, borderRadius: 6, overflow: 'auto' }}>
                  {JSON.stringify(replay.featureSnapshot, null, 2)}
                </pre>
                {replay.market && Object.keys(replay.market).length > 0 && (
                  <>
                    <Typography.Text type="secondary">market</Typography.Text>
                    <pre style={{ fontSize: 12, background: '#f5f7fa', padding: 12, borderRadius: 6, overflow: 'auto' }}>
                      {JSON.stringify(replay.market, null, 2)}
                    </pre>
                  </>
                )}
              </Card>
            </Col>
            {replay.case && (
              <Col span={24}>
                <Card title="关联审核工单" className="content-card">
                  <Descriptions column={3} size="small">
                    <Descriptions.Item label="工单号">{replay.case.caseNo}</Descriptions.Item>
                    <Descriptions.Item label="状态">{replay.case.status}</Descriptions.Item>
                    <Descriptions.Item label="回调">{replay.case.callbackStatus || 'NONE'}</Descriptions.Item>
                    <Descriptions.Item label="审核结论">{replay.case.reviewDecision || '-'}</Descriptions.Item>
                    <Descriptions.Item label="备注" span={2}>{replay.case.reviewComment || '-'}</Descriptions.Item>
                  </Descriptions>
                </Card>
              </Col>
            )}
            {replay.configRelease && (
              <Col span={24}>
                <Card title="当时配置原文" className="content-card">
                  <Row gutter={16}>
                    <Col span={12}>
                      <Typography.Text strong>paramSet</Typography.Text>
                      <pre style={{ fontSize: 11, background: '#f5f7fa', padding: 8, maxHeight: 240, overflow: 'auto' }}>
                        {tryPretty(replay.configRelease.paramSetJson)}
                      </pre>
                    </Col>
                    <Col span={12}>
                      <Typography.Text strong>ruleSet</Typography.Text>
                      <pre style={{ fontSize: 11, background: '#f5f7fa', padding: 8, maxHeight: 240, overflow: 'auto' }}>
                        {tryPretty(replay.configRelease.ruleSetJson)}
                      </pre>
                    </Col>
                  </Row>
                </Card>
              </Col>
            )}
          </Row>
        </>
      )}
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
