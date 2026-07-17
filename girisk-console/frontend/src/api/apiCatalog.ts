export type FieldType = 'string' | 'number' | 'boolean' | 'select';

export interface ApiField {
  name: string;
  label: string;
  type: FieldType;
  required?: boolean;
  default?: string | number | boolean;
  placeholder?: string;
  description?: string;
  options?: { label: string; value: string }[];
}

export interface ApiQueryParam {
  name: string;
  label: string;
  type?: 'string' | 'number';
  required?: boolean;
  default?: string | number;
  description?: string;
}

export interface ApiDefinition {
  id: string;
  group: string;
  name: string;
  method: 'GET' | 'POST';
  path: string;
  summary: string;
  auth: 'jwt' | 'internal' | 'none';
  authHint: string;
  fields?: ApiField[];
  queryParams?: ApiQueryParam[];
}

const decideFields: ApiField[] = [
  { name: 'traceId', label: 'Trace ID', type: 'string', placeholder: '自动生成可留空', description: '链路追踪 ID' },
  { name: 'orderId', label: '订单号', type: 'string', required: true, placeholder: 'ORD-20250525001', description: '业务唯一订单 ID' },
  { name: 'userId', label: '用户 ID', type: 'string', required: true, placeholder: 'U100001' },
  { name: 'operatorId', label: '商户/租户', type: 'string', required: true, default: 'OP-A001', placeholder: 'OP-A001' },
  { name: 'stakeCents', label: '本金(分)', type: 'number', required: true, default: 29900, description: 'stakeCents；也可用 amount 元由适配层换算' },
  { name: 'scenario', label: '场景', type: 'string', default: 'POST_ORDER', description: 'POST_ORDER / SPORTS_BET 等' },
  { name: 'ip', label: 'IP 地址', type: 'string', placeholder: '113.88.1.1' },
  { name: 'deviceId', label: '设备指纹', type: 'string', placeholder: 'DEV-xxx' },
  { name: 'orderCount24h', label: '24h 下单次数', type: 'number' },
  { name: 'amountSum24h', label: '24h 累计金额', type: 'number' },
  { name: 'isNewUser', label: '是否新用户', type: 'boolean', default: false },
  { name: 'deviceRiskScore', label: '设备风险分', type: 'number', default: 0 },
  { name: 'matchCode', label: '比赛编码(体育)', type: 'string', placeholder: 'fixture / match code' },
  { name: 'marketType', label: '玩法(体育)', type: 'string', placeholder: 'ONE_X_TWO' },
  { name: 'selection', label: '投注方向(体育)', type: 'string', placeholder: 'home' },
  { name: 'odds', label: '赔率(体育)', type: 'number', default: 2.1 },
  { name: 'dryRun', label: '仅预览不预占', type: 'boolean', default: true },
];

const evaluateFields: ApiField[] = [
  { name: 'orderId', label: '订单号', type: 'string', required: true, placeholder: 'ORD-20250525001', description: '业务唯一订单 ID' },
  { name: 'userId', label: '用户 ID', type: 'string', required: true, placeholder: 'U100001' },
  { name: 'amount', label: '订单金额', type: 'number', required: true, default: 299, description: '单位与 currency 一致' },
  { name: 'currency', label: '币种', type: 'string', default: 'CNY' },
  { name: 'paymentMethod', label: '支付方式', type: 'select', default: 'ALIPAY', options: [{ label: 'ALIPAY', value: 'ALIPAY' }, { label: 'WECHAT', value: 'WECHAT' }, { label: 'CARD', value: 'CARD' }] },
  { name: 'ip', label: 'IP 地址', type: 'string', placeholder: '113.88.1.1' },
  { name: 'deviceId', label: '设备指纹', type: 'string', placeholder: 'DEV-xxx' },
  { name: 'merchantId', label: '商户 ID', type: 'string', placeholder: 'M001' },
  { name: 'productCategory', label: '商品类目', type: 'string', default: 'GENERAL' },
  { name: 'country', label: '国家', type: 'string', default: 'CN' },
  { name: 'orderCount24h', label: '24h 下单次数', type: 'number', description: '不传则由 Redis/内存自动累计' },
  { name: 'amountSum24h', label: '24h 累计金额', type: 'number' },
  { name: 'isNewUser', label: '是否新用户', type: 'boolean', default: false },
  { name: 'deviceRiskScore', label: '设备风险分', type: 'number', default: 0 },
  { name: 'scenario', label: '场景', type: 'string', default: 'POST_ORDER', description: '匹配策略场景，默认下单后风控' },
];

export const API_CATALOG: ApiDefinition[] = [
  {
    id: 'risk-decide',
    group: '订单对接',
    name: 'GiRisk 决策',
    method: 'POST',
    path: '/girisk/decide',
    summary: 'GiRisk 正式入口：用户规则 → 限额/敞口 → PASS|REJECT|LIMIT|REVIEW，统一写 risk_decision_log。',
    auth: 'jwt',
    authHint: 'Header: Authorization: Bearer <token>，或使用 X-Internal-Key 服务间鉴权',
    fields: decideFields,
  },
  {
    id: 'risk-evaluate',
    group: '订单对接',
    name: '同步风控评估（已废弃）',
    method: 'POST',
    path: '/girisk/evaluate',
    summary: '适配层：转发到 /girisk/decide。请改用统一决策接口。',
    auth: 'jwt',
    authHint: 'Header: Authorization: Bearer <token>，或使用 X-Internal-Key 服务间鉴权',
    fields: evaluateFields,
  },
  {
    id: 'risk-evaluate-internal',
    group: '订单对接',
    name: '内部风控评估（已废弃）',
    method: 'POST',
    path: '/girisk/evaluate/internal',
    summary: '适配层：与 /girisk/evaluate 相同，请改用 /girisk/decide。',
    auth: 'internal',
    authHint: 'Header: X-Internal-Key: girisk-internal-api-key（可在 application.yml 配置）',
    fields: evaluateFields,
  },
  {
    id: 'stream-mock-order',
    group: '流控调试',
    name: '模拟单笔订单',
    method: 'POST',
    path: '/stream/mock-order',
    summary: '本地生成模拟订单并走完整流处理链路（频次 enrichment + 规则 + SSE）。',
    auth: 'jwt',
    authHint: '需要登录 JWT',
    fields: [
      { name: 'userId', label: '用户 ID', type: 'string', placeholder: '留空则随机' },
      { name: 'amount', label: '金额', type: 'number', placeholder: '留空则随机' },
      { name: 'index', label: '序号', type: 'number', description: '影响 mock 数据选取' },
    ],
  },
  {
    id: 'stream-mock-burst',
    group: '流控调试',
    name: 'Burst 批量模拟',
    method: 'POST',
    path: '/stream/mock-burst',
    summary: '连续发送多笔模拟订单，用于压测频次规则。',
    auth: 'jwt',
    authHint: '需要登录 JWT',
    fields: [{ name: 'count', label: '笔数', type: 'number', required: true, default: 5 }],
  },
  {
    id: 'stream-status',
    group: '流控调试',
    name: '流处理状态',
    method: 'GET',
    path: '/stream/status',
    summary: '查看已处理数、错误数、SSE 订阅数等运行指标。',
    auth: 'jwt',
    authHint: '需要登录 JWT',
  },
  {
    id: 'cases-list',
    group: '查询',
    name: '审核工单列表',
    method: 'GET',
    path: '/cases',
    summary: '查询 REVIEW 产生的审核工单，可按状态过滤。',
    auth: 'jwt',
    authHint: '需要登录 JWT',
    queryParams: [
      { name: 'status', label: '状态', type: 'string', description: 'PENDING / APPROVED / REJECTED，留空查全部' },
    ],
  },
];

export const EVALUATE_PRESETS: Record<string, Record<string, unknown>> = {
  normal: { orderId: '', userId: 'U100001', operatorId: 'OP-A001', stakeCents: 29900, amount: 299, ip: '113.88.1.1', deviceId: 'DEV-N001', orderCount24h: 2, amountSum24h: 500, isNewUser: false, deviceRiskScore: 10 },
  large: { orderId: '', userId: 'U200001', operatorId: 'OP-A001', stakeCents: 1580000, amount: 15800, ip: '58.220.1.10', deviceId: 'DEV-L001', orderCount24h: 5, amountSum24h: 30000, isNewUser: false, deviceRiskScore: 30 },
  blacklist: { orderId: '', userId: 'U999999', operatorId: 'OP-A001', stakeCents: 120000, amount: 1200, ip: '192.168.99.100', deviceId: 'DEV-B001', orderCount24h: 1, amountSum24h: 1200, isNewUser: false, deviceRiskScore: 80 },
  newuser: { orderId: '', userId: 'U500001', operatorId: 'OP-A001', stakeCents: 680000, amount: 6800, ip: '114.25.3.8', deviceId: 'DEV-NU01', orderCount24h: 1, amountSum24h: 6800, isNewUser: true, deviceRiskScore: 45 },
  velocity: { orderId: '', userId: 'U600001', operatorId: 'OP-A001', stakeCents: 50000, amount: 500, ip: '120.76.1.1', deviceId: 'DEV-V001', orderCount24h: 25, amountSum24h: 15000, isNewUser: false, deviceRiskScore: 55 },
};

export function defaultFieldValues(def: ApiDefinition): Record<string, unknown> {
  const values: Record<string, unknown> = {};
  def.fields?.forEach((f) => {
    if (f.default !== undefined) values[f.name] = f.default;
  });
  def.queryParams?.forEach((q) => {
    if (q.default !== undefined) values[`__query_${q.name}`] = q.default;
  });
  if (def.fields?.some((f) => f.name === 'orderId')) {
    values.orderId = `ORD-${Date.now()}`;
    values.traceId = values.traceId ?? `tr-${Date.now()}`;
    values.currency = values.currency ?? 'CNY';
    values.country = values.country ?? 'CN';
    values.scenario = values.scenario ?? 'POST_ORDER';
    values.paymentMethod = values.paymentMethod ?? 'ALIPAY';
    values.productCategory = values.productCategory ?? 'GENERAL';
    values.operatorId = values.operatorId ?? 'OP-A001';
  }
  return values;
}
