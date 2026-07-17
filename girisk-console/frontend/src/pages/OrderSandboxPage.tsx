import { Button, Card, Col, Form, Input, InputNumber, Row, Select, Space, Switch, Table, Typography, message } from 'antd';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { riskApi } from '../api/riskClient';
import SandboxTabs from '../components/SandboxTabs';
import type { RiskDecisionResponse, RiskEvaluateResponse } from '../types';
import { DecisionTag, LevelTag } from '../utils/tags';

const presets = [
  { label: '正常用户', value: 'normal' },
  { label: '大额订单', value: 'large' },
  { label: '黑名单用户', value: 'blacklist' },
  { label: '新用户大额', value: 'newuser' },
  { label: '高频下单', value: 'velocity' },
];

type Mode = 'sync' | 'stream';

function isEvaluateResponse(data: unknown): data is RiskEvaluateResponse {
  return typeof data === 'object' && data !== null && 'decision' in data;
}

function isKafkaAck(data: unknown): data is { via: string; orderId: string; message?: string } {
  return typeof data === 'object' && data !== null && 'via' in data && (data as { via: string }).via === 'KAFKA';
}

export default function OrderSandboxPage() {
  const [form] = Form.useForm();
  const [streamForm] = Form.useForm();
  const [mode, setMode] = useState<Mode>('sync');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<RiskDecisionResponse | null>(null);
  const [streamHint, setStreamHint] = useState<string | null>(null);

  const applyPreset = (key: string) => {
    const base = {
      orderId: `ORD-${Date.now()}`,
      currency: 'CNY',
      paymentMethod: 'ALIPAY',
      country: 'CN',
      scenario: 'POST_ORDER',
      operatorId: 'OP-A001',
    };
    const presetsMap: Record<string, object> = {
      normal: { ...base, userId: 'U100001', amount: 299, ip: '113.88.1.1', deviceId: 'DEV-N001', orderCount24h: 2, amountSum24h: 500, isNewUser: false, deviceRiskScore: 10 },
      large: { ...base, userId: 'U200001', amount: 15800, ip: '58.220.1.10', deviceId: 'DEV-L001', orderCount24h: 5, amountSum24h: 30000, isNewUser: false, deviceRiskScore: 30 },
      blacklist: { ...base, userId: 'U999999', amount: 1200, ip: '192.168.99.100', deviceId: 'DEV-B001', orderCount24h: 1, amountSum24h: 1200, isNewUser: false, deviceRiskScore: 80 },
      newuser: { ...base, userId: 'U500001', amount: 6800, ip: '114.25.3.8', deviceId: 'DEV-NU01', orderCount24h: 1, amountSum24h: 6800, isNewUser: true, deviceRiskScore: 45 },
      velocity: { ...base, userId: 'U600001', amount: 500, ip: '120.76.1.1', deviceId: 'DEV-V001', orderCount24h: 25, amountSum24h: 15000, isNewUser: false, deviceRiskScore: 55 },
    };
    form.setFieldsValue(presetsMap[key]);
    setMode('sync');
    setResult(null);
  };

  const onSyncSubmit = async (values: Record<string, unknown>) => {
    setLoading(true);
    setStreamHint(null);
    try {
      const amount = Number(values.amount ?? 0);
      const payload = {
        ...values,
        traceId: `tr-${Date.now()}`,
        stakeCents: Math.round(amount * 100),
        operatorId: values.operatorId || 'OP-A001',
        scenario: 'POST_ORDER',
      };
      const res = await riskApi.decide(payload);
      setResult(res);
      message.success('同步决策完成');
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const sendStreamOne = async () => {
    setLoading(true);
    setResult(null);
    try {
      const values = streamForm.getFieldsValue();
      const body: Record<string, unknown> = {};
      if (values.userId) body.userId = values.userId;
      if (values.amount != null && values.amount !== '') body.amount = values.amount;
      const res = await riskApi.mockOrder(body);
      if (isEvaluateResponse(res)) {
        setStreamHint(`本地流处理完成 · ${res.decision} · 订单 ${res.orderId} · ${res.reason}`);
        message.success('已注入流管线并完成评估');
      } else if (isKafkaAck(res)) {
        setStreamHint(`已发往 Kafka · ${res.orderId} · 可在流量监控看实时推送`);
        message.success('已发送到 Kafka');
      } else {
        setStreamHint('已发送');
        message.success('已发送');
      }
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const sendBurst = async () => {
    setLoading(true);
    setResult(null);
    try {
      const count = Number(streamForm.getFieldValue('burstCount') || 10);
      const res = await riskApi.mockBurst(count);
      setStreamHint(`Burst 完成 · 发送 ${res.sent} 笔 · via ${res.via} · 打开流量监控查看 SSE`);
      message.success(`已发送 ${res.sent} 笔 (${res.via})`);
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
        <p>验证规则与限额；发单压测在此完成，流量监控只负责观察管线状态</p>
      </div>
      <SandboxTabs />

      <Card
        className="content-card"
        style={{ maxWidth: 920 }}
        tabList={[
          { key: 'sync', tab: '同步决策' },
          { key: 'stream', tab: '流管线注入' },
        ]}
        activeTabKey={mode}
        onTabChange={(k) => setMode(k as Mode)}
      >
        {mode === 'sync' ? (
          <>
            <div style={{ marginBottom: 16 }}>
              <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
                快捷场景（一键填表）
              </Typography.Text>
              <Space wrap size={[8, 8]}>
                {presets.map((p) => (
                  <Button key={p.value} size="small" onClick={() => applyPreset(p.value)}>{p.label}</Button>
                ))}
              </Space>
            </div>
            <Form
              form={form}
              layout="vertical"
              onFinish={onSyncSubmit}
              initialValues={{
                currency: 'CNY',
                country: 'CN',
                scenario: 'POST_ORDER',
                operatorId: 'OP-A001',
                isNewUser: false,
                orderCount24h: 0,
                deviceRiskScore: 0,
              }}
            >
              <Row gutter={16}>
                <Col span={12}><Form.Item name="orderId" label="订单号" rules={[{ required: true }]}><Input placeholder="ORD-20250525001" /></Form.Item></Col>
                <Col span={12}><Form.Item name="userId" label="用户ID" rules={[{ required: true }]}><Input placeholder="U100001" /></Form.Item></Col>
                <Col span={12}><Form.Item name="operatorId" label="商户/租户" rules={[{ required: true }]}><Input placeholder="OP-A001" /></Form.Item></Col>
                <Col span={12}><Form.Item name="amount" label="订单金额" rules={[{ required: true }]}><InputNumber style={{ width: '100%' }} min={0} /></Form.Item></Col>
                <Col span={12}><Form.Item name="paymentMethod" label="支付方式"><Select options={[{ value: 'ALIPAY' }, { value: 'WECHAT' }, { value: 'CARD' }]} /></Form.Item></Col>
                <Col span={12}><Form.Item name="ip" label="IP 地址"><Input /></Form.Item></Col>
                <Col span={12}><Form.Item name="deviceId" label="设备指纹"><Input /></Form.Item></Col>
                <Col span={12}><Form.Item name="orderCount24h" label="24h 下单次数"><InputNumber style={{ width: '100%' }} min={0} /></Form.Item></Col>
                <Col span={12}><Form.Item name="amountSum24h" label="24h 累计金额"><InputNumber style={{ width: '100%' }} min={0} /></Form.Item></Col>
                <Col span={12}><Form.Item name="isNewUser" label="新用户" valuePropName="checked"><Switch /></Form.Item></Col>
                <Col span={12}><Form.Item name="deviceRiskScore" label="设备风险分"><InputNumber style={{ width: '100%' }} min={0} max={100} /></Form.Item></Col>
              </Row>
              <Button type="primary" htmlType="submit" loading={loading}>执行统一决策</Button>
            </Form>

            {result && (
              <div className="result-box" style={{ marginTop: 20 }}>
                <Typography.Title level={5} style={{ marginTop: 0 }}>决策结果</Typography.Title>
                <p style={{ marginBottom: 8 }}>
                  <DecisionTag value={result.decision} />{' '}
                  <LevelTag value={result.riskLevel} />{' '}
                  分 {result.riskScore} · {result.latencyMs} ms · {result.strategyCode}
                </p>
                <p style={{ marginBottom: 8 }}>{result.reason}</p>
                {result.caseNo && <p style={{ marginBottom: 8 }}>审核工单：{result.caseNo}</p>}
                <Table
                  size="small"
                  pagination={false}
                  rowKey={(_, i) => String(i)}
                  dataSource={result.reasons || []}
                  columns={[
                    { title: '规则', dataIndex: 'ruleId', width: 140 },
                    { title: '动作', dataIndex: 'action', width: 90, render: (v: string) => <DecisionTag value={v} /> },
                    { title: '说明', dataIndex: 'message', ellipsis: true },
                  ]}
                />
              </div>
            )}
          </>
        ) : (
          <>
            <Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
              走 mock-order / mock-burst：频次 enrichment → 规则引擎 → SSE。不改体育敞口。
              {' '}<Link to="/girisk/stream">打开流量监控</Link>
            </Typography.Paragraph>
            <Form form={streamForm} layout="vertical" initialValues={{ burstCount: 10 }}>
              <Row gutter={16}>
                <Col span={8}>
                  <Form.Item name="userId" label="用户 ID（可选）" tooltip="留空则随机">
                    <Input placeholder="U600001" />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="amount" label="金额（可选）" tooltip="留空则随机">
                    <InputNumber style={{ width: '100%' }} min={1} />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="burstCount" label="Burst 笔数">
                    <InputNumber style={{ width: '100%' }} min={1} max={100} />
                  </Form.Item>
                </Col>
              </Row>
              <Space wrap>
                <Button type="primary" loading={loading} onClick={sendStreamOne}>发送一笔</Button>
                <Button loading={loading} onClick={sendBurst}>Burst 压测</Button>
              </Space>
            </Form>
            {streamHint && (
              <Typography.Paragraph style={{ marginTop: 16, marginBottom: 0 }}>{streamHint}</Typography.Paragraph>
            )}
          </>
        )}
      </Card>
    </>
  );
}
