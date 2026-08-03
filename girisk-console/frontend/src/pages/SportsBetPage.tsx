import { Button, Card, Col, Form, Input, InputNumber, Row, Select, Switch, Table, Tag, Tooltip, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { riskApi } from '../api/riskClient';
import SandboxTabs from '../components/SandboxTabs';
import { BET_PRESET_GROUPS, type BetPreset } from '../config/sportsBetPresets';
import type { RiskDecisionLog, RiskDecisionResponse } from '../types';
import { selectionLabel } from '../utils/sportsLabels';
import { DecisionTag } from '../utils/tags';

const MARKET_OPTIONS = [
  { value: 'ONE_X_TWO', label: '胜负平', selections: ['home', 'draw', 'away'], line: false },
  { value: 'OVER_UNDER', label: '大小球', selections: ['over', 'under'], line: true },
  { value: 'HANDICAP', label: '让球', selections: ['home', 'away'], line: true },
];

function marketLabel(log: RiskDecisionLog): string {
  try {
    const m = log.marketJson ? JSON.parse(log.marketJson) as { selection?: string; line?: string; playType?: string } : {};
    return selectionLabel(m.selection || '-', m.line, m.playType);
  } catch {
    return log.fixtureId || '-';
  }
}

export default function SportsBetPage() {
  const [searchParams] = useSearchParams();
  const [form] = Form.useForm();
  const [result, setResult] = useState<RiskDecisionResponse | null>(null);
  const [logs, setLogs] = useState<RiskDecisionLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [market, setMarket] = useState(MARKET_OPTIONS[0]);

  const syncMarket = (marketType: string) => {
    const m = MARKET_OPTIONS.find((o) => o.value === marketType)!;
    setMarket(m);
  };

  const loadDecisions = async (matchCode: string) => {
    try {
      const all = await riskApi.decisions(50);
      const sports = all.filter((d) => d.scenario === 'SPORTS_BET' || d.source === 'FLINK');
      setLogs(
        (matchCode
          ? sports.filter((d) => d.fixtureId === matchCode)
          : sports
        ).slice(0, 20),
      );
    } catch {
      setLogs([]);
    }
  };

  useEffect(() => {
    const matchCode = searchParams.get('match') || '';
    const marketType = searchParams.get('marketType') || 'ONE_X_TWO';
    const selection = searchParams.get('selection') || 'home';
    syncMarket(marketType);
    if (matchCode) loadDecisions(matchCode);
    else setLogs([]);
    form.setFieldsValue({
      orderId: `BET-${Date.now()}`,
      matchCode: matchCode || undefined,
      marketType,
      selection,
      amount: undefined,
      odds: undefined,
      userId: undefined,
      operatorId: undefined,
      dryRun: true,
    });
  }, [form, searchParams]);

  const onMarketChange = (v: string) => {
    syncMarket(v);
    const m = MARKET_OPTIONS.find((o) => o.value === v)!;
    form.setFieldsValue({
      selection: m.selections[0],
      line: m.line ? form.getFieldValue('line') || '3' : undefined,
    });
  };

  const applyPreset = (preset: BetPreset) => {
    const marketType = String(preset.values.marketType);
    syncMarket(marketType);
    const values: Record<string, unknown> = {
      ...preset.values,
      orderId: `BET-${Date.now()}`,
      userId: form.getFieldValue('userId') || 'U555001',
      operatorId: form.getFieldValue('operatorId') || 'OP-A001',
    };
    if (marketType === 'ONE_X_TWO') {
      values.line = undefined;
    }
    form.setFieldsValue(values);
    setResult(null);
    message.info(`已填充：${preset.label}${preset.hint ? `（${preset.hint}）` : ''}`);
  };

  const submit = async (values: Record<string, unknown>) => {
    setLoading(true);
    try {
      const orderId = String(values.orderId ?? '').trim() || `BET-${Date.now()}`;
      const amount = Number(values.amount);
      const stakeCents = Math.round(amount * 100);
      const payload: Record<string, unknown> = {
        traceId: `tr-${Date.now()}`,
        orderId,
        userId: values.userId,
        operatorId: values.operatorId,
        stakeCents,
        scenario: 'SPORTS_BET',
        matchCode: values.matchCode,
        fixtureId: values.matchCode,
        marketType: values.marketType,
        selection: values.selection,
        odds: values.odds,
        dryRun: values.dryRun,
        skipReserve: values.dryRun,
      };
      if (MARKET_OPTIONS.find((m) => m.value === values.marketType)?.line) {
        payload.line = values.line;
      }
      const res = await riskApi.decide(payload);
      setResult(res);
      if (res.decision === 'PASS') message.success(res.reason);
      else if (res.decision === 'LIMIT') message.warning(res.reason);
      else message.error(res.reason);
      if (!values.dryRun) {
        if (res.decision === 'PASS') {
          await riskApi.confirmOrder(orderId);
          message.info('已 confirm 预占→正式持仓');
        }
        form.setFieldsValue({ orderId: `BET-${Date.now()}` });
      }
      await loadDecisions(String(values.matchCode));
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <div className="page-header">
        <h2>调试沙箱</h2>
        <p>体育投注试算：统一决策核 + 返彩限额 / 预占；责任盘请看实时监控 · 敞口看板</p>
      </div>
      <SandboxTabs />
      <Row gutter={16}>
        <Col xs={24} lg={12}>
          <Card className="content-card" title="投注参数">
            <Form form={form} layout="vertical" onFinish={submit}>
              <Row gutter={12}>
                <Col span={12}><Form.Item name="orderId" label="订单号"><Input placeholder="自动生成" /></Form.Item></Col>
                <Col span={12}><Form.Item name="matchCode" label="比赛" rules={[{ required: true }]}><Input /></Form.Item></Col>
                <Col span={12}><Form.Item name="userId" label="用户ID" rules={[{ required: true }]}><Input /></Form.Item></Col>
                <Col span={12}><Form.Item name="operatorId" label="商户/租户" rules={[{ required: true }]}><Input /></Form.Item></Col>
                <Col span={12}>
                  <Form.Item name="marketType" label="玩法" rules={[{ required: true }]}>
                    <Select options={MARKET_OPTIONS.map((o) => ({ value: o.value, label: o.label }))} onChange={onMarketChange} />
                  </Form.Item>
                </Col>
                {market.line && (
                  <Col span={12}><Form.Item name="line" label="盘口线" rules={[{ required: true }]}><Input placeholder="3 或 1" /></Form.Item></Col>
                )}
                <Col span={12}>
                  <Form.Item name="selection" label="投注方向" rules={[{ required: true }]}>
                    <Select options={market.selections.map((s) => ({ value: s, label: selectionLabel(s, form.getFieldValue('line'), market.value) }))} />
                  </Form.Item>
                </Col>
                <Col span={12}><Form.Item name="amount" label="投注金额" rules={[{ required: true }]}><InputNumber style={{ width: '100%' }} min={1} /></Form.Item></Col>
                <Col span={12}><Form.Item name="odds" label="赔率" rules={[{ required: true }]}><InputNumber style={{ width: '100%' }} min={1} step={0.01} /></Form.Item></Col>
                <Col span={12}><Form.Item name="dryRun" label="仅预览（不预占）" valuePropName="checked"><Switch /></Form.Item></Col>
              </Row>

              <div style={{ marginBottom: 16 }}>
                <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>快捷测试场景</Typography.Text>
                {BET_PRESET_GROUPS.map((group) => (
                  <div key={group.title} style={{ marginBottom: 10 }}>
                    <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>
                      {group.title}
                    </Typography.Text>
                    <div>
                      {group.presets.map((p) => (
                        <Tooltip key={p.key} title={p.hint}>
                          <Button size="small" style={{ marginRight: 8, marginBottom: 8 }} onClick={() => applyPreset(p)}>
                            {p.label}
                          </Button>
                        </Tooltip>
                      ))}
                    </div>
                  </div>
                ))}
              </div>

              <Button type="primary" htmlType="submit" loading={loading}>统一决策</Button>
            </Form>
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          {result && (
            <Card className="content-card" title="决策结果">
              <p><DecisionTag value={result.decision} /> {result.limitMode && <Tag color="orange">限额段</Tag>}</p>
              <p><strong>原因：</strong>{result.reason}</p>
              <p><strong>traceId：</strong>{result.traceId}</p>
              <p><strong>返彩(分)：</strong>{result.payoutCents ?? '-'} · <strong>可接本金(分)：</strong>{result.maxAcceptableStakeCents ?? '-'}</p>
              <p><strong>版本：</strong>epoch {String(result.versions?.configEpoch ?? '-')} / {result.versions?.paramSetVersion}</p>
              <Typography.Text strong>命中 reasons</Typography.Text>
              <Table
                size="small"
                pagination={false}
                rowKey={(_, i) => String(i)}
                style={{ marginTop: 8 }}
                dataSource={result.reasons || []}
                columns={[
                  { title: '规则', dataIndex: 'ruleId', width: 160 },
                  { title: '阶段', dataIndex: 'stage', width: 120 },
                  { title: '动作', dataIndex: 'action', width: 90, render: (v: string) => <DecisionTag value={v} /> },
                  { title: '说明', dataIndex: 'message', ellipsis: true },
                ]}
                expandable={{
                  expandedRowRender: (r) => (
                    <pre style={{ margin: 0, fontSize: 12 }}>{JSON.stringify(r.evidence, null, 2)}</pre>
                  ),
                }}
              />
            </Card>
          )}
          <Card className="content-card" title="最近试算结果" style={{ marginTop: 16 }}>
            <Table
              size="small"
              rowKey="id"
              dataSource={logs}
              pagination={false}
              columns={[
                { title: '时间', dataIndex: 'createdAt', width: 160 },
                { title: '盘口', render: (_: unknown, r: RiskDecisionLog) => marketLabel(r) },
                {
                  title: '金额',
                  width: 80,
                  render: (_: unknown, r: RiskDecisionLog) =>
                    r.stakeCents != null ? (r.stakeCents / 100).toFixed(0) : r.amount,
                },
                { title: '决策', dataIndex: 'decision', width: 80, render: (v: string) => <DecisionTag value={v} /> },
                { title: '原因', dataIndex: 'reason', ellipsis: true },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </>
  );
}
