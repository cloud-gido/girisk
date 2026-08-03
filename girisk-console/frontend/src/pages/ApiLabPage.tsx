import { ApiOutlined, CopyOutlined, SendOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Menu,
  Row,
  Select,
  Space,
  Switch,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import {
  API_CATALOG,
  EVALUATE_PRESETS,
  defaultFieldValues,
  type ApiDefinition,
  type ApiField,
} from '../api/apiCatalog';
import { getToken } from '../auth/session';
import SandboxTabs from '../components/SandboxTabs';
import { DecisionTag, LevelTag } from '../utils/tags';

const BASE = '/api/v1';
const DEFAULT_INTERNAL_KEY = 'girisk-internal-api-key';

const methodColor: Record<string, string> = {
  GET: 'green',
  POST: 'blue',
};

interface ApiResult {
  status: number;
  latencyMs: number;
  body: unknown;
  ok: boolean;
}

function buildBody(def: ApiDefinition, values: Record<string, unknown>): Record<string, unknown> | undefined {
  if (!def.fields?.length) return def.method === 'POST' ? {} : undefined;
  const body: Record<string, unknown> = {};
  def.fields.forEach((f) => {
    const v = values[f.name];
    if (v === undefined || v === null || v === '') return;
    body[f.name] = v;
  });
  return body;
}

function buildUrl(def: ApiDefinition, values: Record<string, unknown>): string {
  // POST 参数一律走 JSON body，禁止拼到 URL
  if (def.method === 'POST') return `${BASE}${def.path}`;
  const params = new URLSearchParams();
  def.queryParams?.forEach((q) => {
    const v = values[`__query_${q.name}`];
    if (v !== undefined && v !== null && v !== '') params.set(q.name, String(v));
  });
  const qs = params.toString();
  return `${BASE}${def.path}${qs ? `?${qs}` : ''}`;
}

function buildCurl(def: ApiDefinition, url: string, body: Record<string, unknown> | undefined, authMode: string, internalKey: string): string {
  const lines = [`curl -X ${def.method} '${window.location.origin}${url}' \\`];
  lines.push(`  -H 'Content-Type: application/json' \\`);
  if (authMode === 'internal') {
    lines.push(`  -H 'X-Internal-Key: ${internalKey}' \\`);
  } else if (getToken()) {
    lines.push(`  -H 'Authorization: Bearer ${getToken()}' \\`);
  }
  if (def.method === 'POST' && body !== undefined) {
    lines.push(`  -d '${JSON.stringify(body)}'`);
  } else {
    lines[lines.length - 1] = lines[lines.length - 1].replace(/ \\$/, '');
  }
  return lines.join('\n');
}

function renderField(field: ApiField) {
  const rules = field.required ? [{ required: true, message: `请填写${field.label}` }] : [];
  const label = (
    <span>
      {field.label}
      {field.required && <Tag color="red" style={{ marginLeft: 6, fontSize: 11, lineHeight: '18px' }}>必填</Tag>}
    </span>
  );

  switch (field.type) {
    case 'number':
      return (
        <Form.Item key={field.name} name={field.name} label={label} rules={rules} tooltip={field.description}>
          <InputNumber style={{ width: '100%' }} placeholder={field.placeholder} min={0} />
        </Form.Item>
      );
    case 'boolean':
      return (
        <Form.Item key={field.name} name={field.name} label={label} valuePropName="checked" tooltip={field.description}>
          <Switch />
        </Form.Item>
      );
    case 'select':
      return (
        <Form.Item key={field.name} name={field.name} label={label} rules={rules} tooltip={field.description}>
          <Select options={field.options} />
        </Form.Item>
      );
    default:
      return (
        <Form.Item key={field.name} name={field.name} label={label} rules={rules} tooltip={field.description}>
          <Input placeholder={field.placeholder} />
        </Form.Item>
      );
  }
}

function isEvaluateResponse(data: unknown): data is {
  decision: string;
  riskScore: number;
  riskLevel: string;
  hitRules: string[];
  reason: string;
  strategyCode: string;
  latencyMs: number;
  caseNo?: string;
  orderId: string;
} {
  return typeof data === 'object' && data !== null && 'decision' in data && 'riskScore' in data;
}

export default function ApiLabPage() {
  const [selectedId, setSelectedId] = useState(API_CATALOG[0].id);
  const [authMode, setAuthMode] = useState<'jwt' | 'internal'>('jwt');
  const [internalKey, setInternalKey] = useState(DEFAULT_INTERNAL_KEY);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<ApiResult | null>(null);
  const [form] = Form.useForm();

  const def = useMemo(() => API_CATALOG.find((a) => a.id === selectedId)!, [selectedId]);

  const menuGroups = useMemo(() => {
    const groups = new Map<string, ApiDefinition[]>();
    API_CATALOG.forEach((api) => {
      const list = groups.get(api.group) || [];
      list.push(api);
      groups.set(api.group, list);
    });
    return groups;
  }, []);

  useEffect(() => {
    form.resetFields();
    form.setFieldsValue(defaultFieldValues(def));
    setResult(null);
    setAuthMode(def.auth === 'internal' ? 'internal' : 'jwt');
  }, [def, form]);

  const send = async (values: Record<string, unknown>) => {
    setLoading(true);
    const body = buildBody(def, values);
    const url = buildUrl(def, values);
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (authMode === 'internal') {
      headers['X-Internal-Key'] = internalKey;
    } else {
      const token = getToken();
      if (token) headers.Authorization = `Bearer ${token}`;
    }

    const start = performance.now();
    try {
      const res = await fetch(url, {
        method: def.method,
        headers,
        body: def.method === 'POST' ? JSON.stringify(body ?? {}) : undefined,
      });
      const latencyMs = Math.round(performance.now() - start);
      let parsed: unknown;
      try {
        parsed = await res.json();
      } catch {
        parsed = { raw: await res.text() };
      }
      setResult({ status: res.status, latencyMs, body: parsed, ok: res.ok });
      if (res.ok) message.success(`请求成功 ${res.status} · ${latencyMs}ms`);
      else message.warning(`HTTP ${res.status} · ${latencyMs}ms`);
    } catch (e) {
      setResult({
        status: 0,
        latencyMs: Math.round(performance.now() - start),
        body: { error: (e as Error).message },
        ok: false,
      });
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const applyPreset = (key: string) => {
    const preset = EVALUATE_PRESETS[key];
    if (!preset) return;
    form.setFieldsValue({ ...preset, orderId: `ORD-${Date.now()}` });
  };

  const copyCurl = () => {
    const values = form.getFieldsValue(true);
    const curl = buildCurl(def, buildUrl(def, values), buildBody(def, values), authMode, internalKey);
    navigator.clipboard.writeText(curl);
    message.success('cURL 已复制');
  };

  const responseData = result?.body && typeof result.body === 'object' && result.body !== null && 'data' in result.body
    ? (result.body as { data: unknown }).data
    : null;

  const requiredFields = def.fields?.filter((f) => f.required) ?? [];
  const optionalFields = def.fields?.filter((f) => !f.required) ?? [];

  return (
    <>
      <div className="page-header">
        <h2>调试沙箱</h2>
        <p>接口联调：选接口、填参、看返回 — 对接前本地验证，非生产监控</p>
      </div>
      <SandboxTabs />

      <Row gutter={16}>
        <Col xs={24} lg={6}>
          <Card className="content-card" title="接口列表" styles={{ body: { padding: 0 } }}>
            {Array.from(menuGroups.entries()).map(([group, items]) => (
              <div key={group}>
                <div style={{ padding: '8px 16px', fontSize: 12, color: '#8c8c8c', background: '#fafafa' }}>{group}</div>
                <Menu
                  mode="inline"
                  selectedKeys={[selectedId]}
                  items={items.map((api) => ({
                    key: api.id,
                    label: (
                      <Space size={4}>
                        <Tag color={methodColor[api.method]} style={{ margin: 0, fontSize: 11 }}>{api.method}</Tag>
                        <span style={{ fontSize: 13 }}>{api.name}</span>
                      </Space>
                    ),
                  }))}
                  onClick={({ key }) => setSelectedId(key)}
                  style={{ border: 'none' }}
                />
              </div>
            ))}
          </Card>
        </Col>

        <Col xs={24} lg={10}>
          <Card className="content-card" title={
            <Space>
              <Tag color={methodColor[def.method]}>{def.method}</Tag>
              <Typography.Text code>{BASE}{def.path}</Typography.Text>
            </Space>
          }>
            <Alert type="info" showIcon message={def.summary} description={def.authHint} style={{ marginBottom: 16 }} />

            <Form layout="vertical" form={form} onFinish={send}>
              <Row gutter={16} style={{ marginBottom: 8 }}>
                <Col span={12}>
                  <Form.Item label="鉴权方式">
                    <Select
                      value={authMode}
                      onChange={setAuthMode}
                      options={[
                        { label: 'JWT（当前登录 Token）', value: 'jwt' },
                        { label: 'X-Internal-Key（服务间）', value: 'internal' },
                      ]}
                    />
                  </Form.Item>
                </Col>
                {authMode === 'internal' && (
                  <Col span={12}>
                    <Form.Item label="Internal Key">
                      <Input value={internalKey} onChange={(e) => setInternalKey(e.target.value)} placeholder={DEFAULT_INTERNAL_KEY} />
                    </Form.Item>
                  </Col>
                )}
              </Row>

              {def.queryParams?.map((q) => (
                <Form.Item
                  key={q.name}
                  name={`__query_${q.name}`}
                  label={
                    <span>
                      {q.label} <Typography.Text type="secondary">(Query)</Typography.Text>
                      {q.required && <Tag color="red" style={{ marginLeft: 6, fontSize: 11 }}>必填</Tag>}
                    </span>
                  }
                  rules={q.required ? [{ required: true }] : []}
                  tooltip={q.description}
                >
                  {q.type === 'number'
                    ? <InputNumber style={{ width: '100%' }} min={1} />
                    : <Input placeholder={q.description} />}
                </Form.Item>
              ))}

              {requiredFields.length > 0 && (
                <>
                  <Typography.Text strong style={{ display: 'block', marginBottom: 12 }}>必填参数</Typography.Text>
                  <Row gutter={16}>{requiredFields.map((f) => <Col key={f.name} span={12}>{renderField(f)}</Col>)}</Row>
                </>
              )}

              {optionalFields.length > 0 && (
                <>
                  <Typography.Text strong style={{ display: 'block', margin: '8px 0 12px' }}>可选参数</Typography.Text>
                  <Row gutter={16}>{optionalFields.map((f) => <Col key={f.name} span={12}>{renderField(f)}</Col>)}</Row>
                </>
              )}

              {def.id.startsWith('risk-evaluate') && (
                <div style={{ marginBottom: 16 }}>
                  <Typography.Text type="secondary" style={{ marginRight: 8 }}>快捷填充：</Typography.Text>
                  {Object.keys(EVALUATE_PRESETS).map((k) => (
                    <Button key={k} size="small" style={{ marginRight: 8, marginBottom: 8 }} onClick={() => applyPreset(k)}>
                      {({ normal: '正常', large: '大额', blacklist: '黑名单', newuser: '新用户大额', velocity: '高频' })[k]}
                    </Button>
                  ))}
                </div>
              )}

              <Space>
                <Button type="primary" htmlType="submit" icon={<SendOutlined />} loading={loading} size="large">
                  发送请求
                </Button>
                <Button icon={<CopyOutlined />} onClick={copyCurl}>复制 cURL</Button>
              </Space>
            </Form>
          </Card>
        </Col>

        <Col xs={24} lg={8}>
          <Card className="content-card" title="响应结果">
            {!result ? (
              <div style={{ textAlign: 'center', padding: '48px 0', color: '#bfbfbf' }}>
                <ApiOutlined style={{ fontSize: 40, marginBottom: 12 }} />
                <div>填写参数后点击「发送请求」</div>
              </div>
            ) : (
              <Tabs
                items={[
                  {
                    key: 'summary',
                    label: '摘要',
                    children: (
                      <>
                        <Space style={{ marginBottom: 16 }}>
                          <Tag color={result.ok ? 'success' : 'error'}>HTTP {result.status || 'ERR'}</Tag>
                          <Tag>{result.latencyMs} ms</Tag>
                        </Space>
                        {isEvaluateResponse(responseData) && (
                          <div className="result-box" style={{ marginTop: 0 }}>
                            <p><strong>决策：</strong><DecisionTag value={responseData.decision} /></p>
                            <p><strong>风险分：</strong>{responseData.riskScore} <LevelTag value={responseData.riskLevel} /></p>
                            <p><strong>订单：</strong>{responseData.orderId}</p>
                            <p><strong>命中规则：</strong>{responseData.hitRules?.length ? responseData.hitRules.join(', ') : '无'}</p>
                            <p><strong>原因：</strong>{responseData.reason}</p>
                            {responseData.caseNo && <p><strong>审核工单：</strong>{responseData.caseNo}</p>}
                          </div>
                        )}
                      </>
                    ),
                  },
                  {
                    key: 'json',
                    label: 'JSON',
                    children: (
                      <pre className="api-lab-json">{JSON.stringify(result.body, null, 2)}</pre>
                    ),
                  },
                ]}
              />
            )}
          </Card>
        </Col>
      </Row>
    </>
  );
}
