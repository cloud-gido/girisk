import {
  Alert,
  Button,
  Card,
  Col,
  Collapse,
  Descriptions,
  Empty,
  Form,
  Input,
  Row,
  Space,
  Steps,
  Switch,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import { riskApi } from '../api/riskClient';
import GateSummaryPanel from '../components/GateSummaryPanel';
import { isAdmin } from '../auth/session';
import { useDisplayTimeZone } from '../hooks/useDisplayTimeZone';
import type { DecisionReplay, DorisAuditConfigView, RiskDecisionLog } from '../types';
import { formatInTimeZone } from '../utils/datetime';
import { buildGateSummary } from '../utils/gateSummary';
import { DecisionTag, LevelTag } from '../utils/tags';

export default function ReplayPage() {
  const [params, setParams] = useSearchParams();
  const [orderId, setOrderId] = useState(params.get('orderId') || '');
  const [traceId, setTraceId] = useState(params.get('traceId') || '');
  const [fixtureId, setFixtureId] = useState(params.get('fixtureId') || '');
  const [fixtureHits, setFixtureHits] = useState<RiskDecisionLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [replay, setReplay] = useState<DecisionReplay | null>(null);
  const displayTz = useDisplayTimeZone();
  const admin = isAdmin();
  const [doris, setDoris] = useState<DorisAuditConfigView | null>(null);
  const [dorisLoading, setDorisLoading] = useState(false);
  const [form] = Form.useForm();

  const loadDorisConfig = useCallback(async () => {
    try {
      const cfg = await riskApi.dorisAuditConfig();
      setDoris(cfg);
      form.setFieldsValue({
        enabled: cfg.enabled,
        host: cfg.host || '',
        port: cfg.port || 9030,
        database: cfg.database || 'girisk',
        username: cfg.username || 'root',
        password: '',
        decisionTable: cfg.decisionTable || 'risk_decision_log',
        configTable: cfg.configTable || 'risk_config_log',
      });
    } catch {
      // 无 audit:read 时忽略
    }
  }, [form]);

  useEffect(() => {
    void loadDorisConfig();
  }, [loadDorisConfig]);

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
      setFixtureHits([]);
      setParams(by === 'order' ? { orderId: value.trim() } : { traceId: value.trim() });
    } catch (e: unknown) {
      setReplay(null);
      message.error(e instanceof Error ? e.message : '回放失败');
    } finally {
      setLoading(false);
    }
  };

  const searchByFixture = async (value: string) => {
    if (!value.trim()) {
      message.warning('请输入赛事 ID');
      return;
    }
    setLoading(true);
    try {
      const list = await api.decisionsByFixture(value.trim());
      setFixtureHits(list);
      setReplay(null);
      setParams({ fixtureId: value.trim() });
      if (list.length === 0) {
        message.info('未找到该赛事决策');
      } else if (list[0]?.orderId) {
        // 默认回放最新一笔，列表可切换
        const data = await api.replayByOrder(list[0].orderId);
        setReplay(data);
        setOrderId(list[0].orderId);
      }
    } catch (e: unknown) {
      setFixtureHits([]);
      setReplay(null);
      message.error(e instanceof Error ? e.message : '按赛事查询失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const oid = params.get('orderId');
    const tid = params.get('traceId');
    const fid = params.get('fixtureId');
    if (oid) {
      setOrderId(oid);
      void load('order', oid);
    } else if (tid) {
      setTraceId(tid);
      void load('trace', tid);
    } else if (fid) {
      setFixtureId(fid);
      void searchByFixture(fid);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const dorisBody = (values: Record<string, unknown>, forceEnabled?: boolean) => ({
    enabled: forceEnabled ?? !!values.enabled,
    host: String(values.host || '').trim(),
    port: Number(values.port) || 9030,
    database: String(values.database || 'girisk').trim() || 'girisk',
    username: String(values.username || 'root').trim() || 'root',
    // 空 = 无密码（内网 Doris 常见）
    password: values.password == null ? '' : String(values.password),
    decisionTable: String(values.decisionTable || 'risk_decision_log').trim() || 'risk_decision_log',
    configTable: String(values.configTable || 'risk_config_log').trim() || 'risk_config_log',
  });

  const saveDoris = async () => {
    const values = await form.validateFields();
    setDorisLoading(true);
    try {
      const cfg = await riskApi.putDorisAuditConfig(dorisBody(values));
      setDoris(cfg);
      message.success(
        cfg.available
          ? '已保存，Doris 可用（回放优先 Doris）'
          : `已保存；当前回退 PostgreSQL${cfg.lastError ? `（${cfg.lastError}）` : ''}`,
      );
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : '保存失败');
    } finally {
      setDorisLoading(false);
    }
  };

  const testDoris = async () => {
    const values = await form.validateFields([
      'host',
      'port',
      'database',
      'username',
      'password',
      'decisionTable',
      'configTable',
    ]);
    setDorisLoading(true);
    try {
      const r = await riskApi.testDorisAuditConfig(dorisBody(values, true));
      if (r.ok) {
        message.success(r.message || '连接成功');
      } else {
        message.error(r.message || '连接失败');
      }
    } catch (e: unknown) {
      message.error(e instanceof Error ? e.message : '测试失败');
    } finally {
      setDorisLoading(false);
    }
  };

  const d = replay?.decision;
  const activeSource = doris?.activeSource || 'postgres';

  return (
    <>
      <div className="page-header">
        <h2>风险回放</h2>
        <p>生产决策解释：按订单 / Trace 还原 Gate1 限额、Gate2 敞口、规则版本与理由</p>
      </div>

      <Card className="content-card" style={{ marginBottom: 16 }} size="small">
        <Space wrap>
          <Typography.Text type="secondary">当前回放数据源</Typography.Text>
          <Tag color={activeSource === 'doris' ? 'purple' : 'blue'}>
            {activeSource === 'doris' ? 'Doris' : 'PostgreSQL'}
          </Tag>
          {doris?.enabled && !doris.available && doris.lastError ? (
            <Typography.Text type="danger" style={{ fontSize: 12 }}>
              Doris 已启用但不可用：{doris.lastError}
            </Typography.Text>
          ) : null}
        </Space>
      </Card>

      {admin ? (
        <Collapse
          className="content-card"
          style={{ marginBottom: 16 }}
          items={[{
            key: 'doris',
            label: 'Doris 审计数据源（仅管理员）',
            children: (
              <Form
                form={form}
                layout="vertical"
                initialValues={{
                  enabled: false,
                  host: '',
                  port: 9030,
                  database: 'girisk',
                  username: 'root',
                  password: '',
                  decisionTable: 'risk_decision_log',
                  configTable: 'risk_config_log',
                }}
              >
                <Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
                  填写 FE 地址与审计表名（默认端口 9030、库 girisk、用户 root、表 risk_decision_log）。
                  内网无密码时密码留空。启用后回放优先 Doris，失败仍回退 PostgreSQL。
                </Typography.Paragraph>
                <Form.Item name="enabled" label="启用 Doris 优先回放" valuePropName="checked">
                  <Switch checkedChildren="开" unCheckedChildren="关" />
                </Form.Item>
                <Form.Item
                  name="host"
                  label="主机"
                  rules={[
                    ({ getFieldValue }) => ({
                      validator(_, value) {
                        if (!getFieldValue('enabled') || (value && String(value).trim())) {
                          return Promise.resolve();
                        }
                        return Promise.reject(new Error('启用时请填写主机'));
                      },
                    }),
                  ]}
                >
                  <Input placeholder="例如 test-doris-cluster-fe-internal.bigdata.svc.cluster.local" />
                </Form.Item>
                <Space wrap style={{ display: 'flex', width: '100%' }} size="middle">
                  <Form.Item name="port" label="端口" style={{ marginBottom: 16, width: 140 }}>
                    <Input type="number" placeholder="9030" />
                  </Form.Item>
                  <Form.Item name="database" label="数据库" style={{ marginBottom: 16, width: 180 }}>
                    <Input placeholder="girisk" />
                  </Form.Item>
                  <Form.Item name="username" label="用户名" style={{ marginBottom: 16, width: 160 }}>
                    <Input placeholder="root" autoComplete="off" />
                  </Form.Item>
                </Space>
                <Form.Item name="password" label="密码" extra="无密码请留空">
                  <Input.Password placeholder="可选" autoComplete="new-password" />
                </Form.Item>
                <Form.Item
                  name="decisionTable"
                  label="决策审计表"
                  rules={[
                    ({ getFieldValue }) => ({
                      validator(_, value) {
                        if (!getFieldValue('enabled') || (value && String(value).trim())) {
                          return Promise.resolve();
                        }
                        return Promise.reject(new Error('启用时请填写决策表名'));
                      },
                    }),
                  ]}
                  extra="例如 ods_gameline_risk_decision_log"
                >
                  <Input placeholder="risk_decision_log" />
                </Form.Item>
                <Form.Item
                  name="configTable"
                  label="配置审计表"
                  extra="回放关联配置版本时使用；可先填决策表同库的配置表"
                >
                  <Input placeholder="risk_config_log" />
                </Form.Item>
                <Space>
                  <Button loading={dorisLoading} onClick={() => void testDoris()}>
                    测试连接
                  </Button>
                  <Button type="primary" loading={dorisLoading} onClick={() => void saveDoris()}>
                    保存并生效
                  </Button>
                </Space>
              </Form>
            ),
          }]}
        />
      ) : null}

      <Card className="content-card" style={{ marginBottom: 16 }}>
        <Space wrap>
          <Input
            placeholder="订单号，如 ORD-20260715001"
            value={orderId}
            onChange={(e) => setOrderId(e.target.value)}
            onPressEnter={() => void load('order', orderId)}
            style={{ width: 240 }}
          />
          <Button type="primary" loading={loading} onClick={() => void load('order', orderId)}>
            按订单回放
          </Button>
          <Input
            placeholder="赛事 ID，如 13375300"
            value={fixtureId}
            onChange={(e) => setFixtureId(e.target.value)}
            onPressEnter={() => void searchByFixture(fixtureId)}
            style={{ width: 200 }}
          />
          <Button loading={loading} onClick={() => void searchByFixture(fixtureId)}>
            按赛事筛选
          </Button>
          <Input
            placeholder="traceId，如 tr-7f3a9c"
            value={traceId}
            onChange={(e) => setTraceId(e.target.value)}
            onPressEnter={() => void load('trace', traceId)}
            style={{ width: 200 }}
          />
          <Button loading={loading} onClick={() => void load('trace', traceId)}>
            按 Trace 回放
          </Button>
        </Space>
      </Card>

      {fixtureHits.length > 0 && (
        <Card
          className="content-card"
          size="small"
          title={`赛事 ${fixtureId || params.get('fixtureId') || ''} · ${fixtureHits.length} 条决策`}
          style={{ marginBottom: 16 }}
        >
          <Table
            size="small"
            rowKey="id"
            pagination={{ pageSize: 8 }}
            dataSource={fixtureHits}
            columns={[
              {
                title: '时间',
                dataIndex: 'createdAt',
                width: 170,
                render: (v: string) => formatInTimeZone(v, displayTz),
              },
              { title: '订单号', dataIndex: 'orderId', width: 180 },
              {
                title: '决策',
                dataIndex: 'decision',
                width: 90,
                render: (v: string) => <DecisionTag value={v} />,
              },
              {
                title: '等级',
                dataIndex: 'riskLevel',
                width: 90,
                render: (v: string) => <LevelTag value={v} />,
              },
              { title: '原因', dataIndex: 'reason', ellipsis: true },
              {
                title: '操作',
                width: 90,
                render: (_: unknown, r: RiskDecisionLog) => (
                  <Button
                    type="link"
                    size="small"
                    onClick={() => {
                      setOrderId(r.orderId);
                      void load('order', r.orderId);
                    }}
                  >
                    回放
                  </Button>
                ),
              },
            ]}
          />
        </Card>
      )}

      {!replay && !loading && fixtureHits.length === 0 && (
        <Empty description="输入订单号、赛事 ID 或 Trace 开始回放" />
      )}

      {replay && d && (
        <>
          <Alert
            type={replay.explainable ? 'success' : 'warning'}
            showIcon
            style={{ marginBottom: 16 }}
            message={replay.explainable ? '本条决策可完整解释与回溯' : '本条决策缺少 reasons/versions 快照（旧数据）'}
            description={
              replay.auditSource
                ? `本条审计数据源：${replay.auditSource === 'doris' ? 'Doris（Kafka 原样）' : 'PostgreSQL（运营库）'}`
                : undefined
            }
          />

          <Steps
            style={{ marginBottom: 24 }}
            items={[
              {
                title: '请求入库',
                description: formatInTimeZone(d.createdAt, displayTz),
                status: 'finish',
              },
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
